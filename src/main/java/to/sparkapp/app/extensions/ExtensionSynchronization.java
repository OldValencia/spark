package to.sparkapp.app.extensions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import to.sparkapp.app.browser.WebviewManager;

/** Makes the browser profile match the managed extension folders. */
@Slf4j
@RequiredArgsConstructor
final class ExtensionSynchronization {
    static final int BROWSER_TIMEOUT_SECONDS = 30;

    private final Consumer<String> progress;

    List<ExtensionEntry> refresh(WebviewManager browser) throws Exception {
        var startedAt = System.nanoTime();
        progress.accept("Reading installed extensions…");

        var store = new ExtensionStore();
        var installed = browser.listExtensions().get(BROWSER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        var directories = store.availableDirectories();
        var folders = new ExtensionFolderSynchronization(browser, store, installed, progress);
        var entries = new ArrayList<ExtensionEntry>();
        for (var directory : directories) {
            var position = (entries.size() + 1) + "/" + directories.size();
            entries.add(folders.synchronize(directory, position));
        }

        new ExtensionStaleCleanup(browser, store).run(installed, directories);
        log.info("Extension synchronization finished: count={}, elapsedMs={}", entries.size(), elapsedMillis(startedAt));

        return List.copyOf(entries);
    }

    static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
