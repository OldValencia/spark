package to.sparkapp.app.extensions;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;
import to.sparkapp.app.browser.WebviewManager;
import to.sparkapp.app.utils.SystemUtils;

/** Serializes extension work on one background thread and remembers the last synchronized list. */
@Slf4j
public final class ExtensionManager {
    /** WebView2 restores installed extensions itself; verification can wait until the first page has loaded. */
    private static final long STARTUP_DELAY_SECONDS = 5;
    private static final ExtensionManager INSTANCE = new ExtensionManager();

    private final AtomicBoolean started = new AtomicBoolean();
    private final ExtensionProgressBroadcast progress = new ExtensionProgressBroadcast();
    private final ExtensionUpdateService updateService = new ExtensionUpdateService();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(action -> {
        var thread = new Thread(action, "extension-management");
        thread.setDaemon(true);
        return thread;
    });
    private volatile List<ExtensionEntry> entries;
    private CompletableFuture<List<ExtensionEntry>> refreshing;
    private WebviewManager refreshingBrowser;

    public static ExtensionManager instance() {
        return INSTANCE;
    }

    public void start(WebviewManager browser) {
        if (!SystemUtils.isWindows() || !started.compareAndSet(false, true)) {
            return;
        }

        var delayed = CompletableFuture.delayedExecutor(STARTUP_DELAY_SECONDS, TimeUnit.SECONDS);
        CompletableFuture.runAsync(() -> refresh(browser).whenComplete((value, error) -> {
            if (error != null) {
                started.set(false);
            }
        }), delayed);
    }

    /** The most recent synchronized list, available without touching files or the browser. */
    public Optional<List<ExtensionEntry>> cachedEntries() {
        return Optional.ofNullable(entries);
    }

    /** Completes when the running synchronization finishes; popups wait so they never start stale files. */
    public synchronized CompletableFuture<Void> whenSynchronized() {
        if (refreshing == null) {
            return CompletableFuture.completedFuture(null);
        }

        return refreshing.handle((value, error) -> null);
    }

    /** Overlapping requests for the same browser share the running synchronization. */
    public synchronized CompletableFuture<List<ExtensionEntry>> refresh(WebviewManager browser) {
        if (refreshing != null && !refreshing.isDone() && refreshingBrowser == browser) {
            log.info("Extension refresh joined an existing synchronization");
            return refreshing;
        }

        refreshingBrowser = browser;
        refreshing = submit(() -> synchronize(browser));
        return refreshing;
    }

    public CompletableFuture<List<ExtensionEntry>> importArchive(WebviewManager browser, Path archive) {
        return submit(() -> {
            progress.report("Importing " + archive.getFileName() + "…");
            updates(browser).importArchive(archive);
            return synchronize(browser);
        });
    }

    public CompletableFuture<List<ExtensionEntry>> installFromStore(WebviewManager browser, String storeId) {
        return submit(() -> {
            progress.report("Downloading from the Chrome Web Store…");
            updates(browser).installFromStore(storeId);
            return synchronize(browser);
        });
    }

    /** Folders whose update service offers a newer version, mapped to that version. */
    public CompletableFuture<Map<Path, String>> checkUpdates(WebviewManager browser) {
        return submit(() -> {
            progress.report("Checking for updates…");
            return updates(browser).check();
        });
    }

    public CompletableFuture<List<ExtensionEntry>> update(WebviewManager browser, ExtensionEntry entry) {
        return submit(() -> {
            progress.report("Updating " + entry.manifest().name() + "…");
            updates(browser).update(entry.manifest().directory());
            return synchronize(browser);
        });
    }

    public CompletableFuture<List<ExtensionEntry>> setEnabled(WebviewManager browser, ExtensionEntry entry, boolean enabled) {
        return submit(() -> {
            progress.report((enabled ? "Enabling " : "Disabling ") + entry.manifest().name() + "…");
            new ExtensionChanges(browser, new ExtensionStore()).setEnabled(entry, enabled);
            return synchronize(browser);
        });
    }

    public CompletableFuture<List<ExtensionEntry>> remove(WebviewManager browser, ExtensionEntry entry) {
        return submit(() -> {
            progress.report("Removing " + entry.manifest().name() + "…");
            new ExtensionChanges(browser, new ExtensionStore()).remove(entry);
            return synchronize(browser);
        });
    }

    public void addProgressListener(Consumer<ExtensionProgress> listener) {
        progress.add(listener);
    }

    public void removeProgressListener(Consumer<ExtensionProgress> listener) {
        progress.remove(listener);
    }

    /** The innermost cause's message, suitable for showing to the user. */
    public static String message(Throwable error) {
        var cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }

        return cause.getMessage() == null ? "The extension operation failed" : cause.getMessage();
    }

    private ExtensionUpdates updates(WebviewManager browser) throws IOException {
        return new ExtensionUpdates(browser, new ExtensionStore(), updateService);
    }

    private List<ExtensionEntry> synchronize(WebviewManager browser) throws Exception {
        var refreshed = new ExtensionSynchronization(progress::report).refresh(browser);
        entries = refreshed;
        return refreshed;
    }

    private <T> CompletableFuture<T> submit(Work<T> work) {
        if (!SystemUtils.isWindows()) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("Extensions are available on Windows only."));
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                return work.run();
            } catch (Exception error) {
                throw new CompletionException(error);
            }
        }, worker);
    }

    private interface Work<T> {
        T run() throws Exception;
    }
}
