package to.sparkapp.app.browser;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

import com.sun.jna.Pointer;

import javafx.application.Platform;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import to.sparkapp.app.config.AiConfiguration.AiConfig;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.extensions.ExtensionManager;
import to.sparkapp.app.utils.SystemUtils;

@Slf4j
public class WebviewManager {
    private static final int EXTENSION_TIMEOUT_SECONDS = 30;
    private static final int CONTEXT_WINDOW_TIMEOUT_SECONDS = 5;
    private static final int PAGE_NAMES_TIMEOUT_SECONDS = 2;
    /** Sites often set their title just after the first load; that is not an update yet. */
    private static final long TITLE_QUIET_NANOS = 2_000_000_000L;

    private final AppPreferences appPreferences;
    private final BrowserPresentation presentation;
    private Runnable shutdownCallback;
    private volatile Webview webview;

    private final AtomicBoolean ready = new AtomicBoolean(false);
    private final AtomicBoolean disposed = new AtomicBoolean(false);
    private final AtomicBoolean isStarting = new AtomicBoolean(false);

    private final WebviewZoomManager zoomManager;
    private final WebviewNavigator navigator;

    private final Queue<Runnable> pending = new ArrayDeque<>();
    @Getter
    private volatile boolean loading = true;
    @Getter
    private volatile boolean pagePresentable;
    @Setter
    private Runnable onPagePresentable;
    @Getter
    private volatile String loadError;
    private volatile boolean processFailed;
    private volatile boolean recreateOnRetry;
    private volatile boolean restartRequested;
    private volatile boolean muted;
    private volatile boolean mobile;
    @Getter
    private volatile boolean extraPageOpen;
    private long navigationId;
    @Setter
    private Consumer<Boolean> onLoadingChanged;
    @Setter
    private Consumer<String> onLoadError;

    private void loadingChanged(boolean value) {
        loading = value;
        pagePresentable = !value && loadError == null;

        if (onLoadingChanged != null) {
            Platform.runLater(() -> onLoadingChanged.accept(value));
        }
    }

    @Setter
    private Runnable onReadyCallback;

    @Setter
    private Consumer<Boolean> onExtraPageChanged;

    @Setter
    private Consumer<SiteShortcut> onSiteShortcut;

    /** Runs when a page of the site changes its title after the site first finished loading. */
    @Setter
    private Runnable onPageUpdated;
    private volatile boolean firstPageLoaded;
    private volatile long titlesCountFrom;

    public WebviewManager(AppPreferences appPreferences) {
        this.appPreferences = appPreferences;
        this.presentation = new BrowserPresentation(appPreferences);
        this.zoomManager = new WebviewZoomManager(appPreferences, this::applyZoomFactor);
        this.navigator = new WebviewNavigator(this::dispatch, this::loadURL, this::reapplyDocumentZoom);
    }

    public void init(String startUrl, long parentHandle, int x, int y, int width, int height) {
        if (disposed.get()) {
            return;
        }

        presentation.initialize(parentHandle, x, y, width, height);

        startWebviewThread(startUrl);
    }

    private synchronized void startWebviewThread(String initialUrl) {
        if (disposed.get() || !isStarting.compareAndSet(false, true)) {
            return;
        }

        Thread.ofPlatform().daemon().name("spark-webview-thread-" + System.currentTimeMillis())
                .start(() -> runBrowser(initialUrl));
    }

    private void runBrowser(String initialUrl) {
        try {
            initializeBrowser(initialUrl);
            webview.dispatch(this::publishReadyBrowser);
            webview.run();
        } catch (Throwable failure) {
            log.error("WebviewManager: Webview thread encountered an error", failure);
            loadError = "The browser could not start. Try opening the site again.";
            loadingChanged(false);
            notifyLoadError();
        } finally {
            finishBrowserThread();
        }
    }

    private void initializeBrowser(String initialUrl) {
        ready.set(false);
        processFailed = false;
        recreateOnRetry = false;

        // The embedded creation path treats the host as its own window. Creating a
        // standalone browser before reparenting keeps native geometry calls off the host.
        webview = new Webview(false);
        presentation.setNativeHandle(webview.getNativeWindowPointer());
        presentation.attach(true);
        observeWindowsBrowser();
        BrowserPageBindings.install(webview, navigator, zoomManager, () -> loadingChanged(false));
        webview.setInitScript(BrowserPageScripts.INIT_SCRIPT);

        var url = initialUrl != null ? initialUrl : "about:blank";
        synchronized (this) {
            if (pending.isEmpty()) {
                webview.loadURL(url);
            }
        }
    }

    private void observeWindowsBrowser() {
        if (!SystemUtils.isWindows()) {
            return;
        }

        var browser = webview.windows();
        // Set before the first navigation, so the page never appears at the wrong size.
        browser.setZoomFactor(zoomManager.currentFactor());
        browser.setMuted(muted);
        browser.setMobile(mobile);
        browser.setPresentation(false, presentation.wantsLowMemory());
        browser.observeProcessFailures(this::handleProcessFailure);
        browser.observeShortcuts(this::siteShortcutPressed);
        browser.observeTitles(this::titleChanged);
        browser.observe(this::navigationStarting, this::navigationCompleted);
        browser.observeDocument(this::documentReady);
        browser.observeSource(url -> Platform.runLater(() -> navigator.handleUrlChange(url)));
        browser.setExtraPageChanged(extra -> {
            extraPageOpen = extra;
            Platform.runLater(() -> extraPageChanged(extra));
        });
    }

    private void extraPageChanged(boolean extra) {
        if (extra) {
            navigator.forgetPageUrl();
        }

        if (onExtraPageChanged != null) {
            onExtraPageChanged.accept(extra);
        }
    }

    /** Titles set while the site first loads, or right after, are not news. */
    private void titleChanged() {
        var handler = onPageUpdated;

        if (firstPageLoaded && System.nanoTime() - titlesCountFrom >= 0 && handler != null) {
            Platform.runLater(handler);
        }
    }

    private void siteShortcutPressed(SiteShortcut shortcut) {
        var handler = onSiteShortcut;

        if (handler != null) {
            Platform.runLater(() -> handler.accept(shortcut));
        }
    }

    private void documentReady(long id) {
        if (disposed.get() || processFailed || id != navigationId || loadError != null ||
                "about:blank".equals(webview.windows().source())) {
            return;
        }

        allowPagePresentation();
    }

    public void allowPagePresentation() {
        if (disposed.get() || processFailed || loadError != null) {
            return;
        }

        pagePresentable = true;

        if (onPagePresentable != null) {
            Platform.runLater(onPagePresentable);
        }
    }

    private void navigationStarting(long id) {
        if (processFailed || disposed.get()) {
            return;
        }

        navigationId = id;
        loadError = null;
        loadingChanged(true);
    }

    private void navigationCompleted(long id, boolean success) {
        if (processFailed || disposed.get() || id != navigationId) {
            return;
        }

        var url = webview.windows().source();

        if ("about:blank".equals(url)) {
            return;
        }

        Platform.runLater(() -> navigator.handleUrlChange(url));
        if (!firstPageLoaded) {
            titlesCountFrom = System.nanoTime() + TITLE_QUIET_NANOS;
            firstPageLoaded = true;
        }

        if (!success) {
            loadError = "This page could not be loaded. Check your connection and try again.";
            notifyLoadError();
        }

        loadingChanged(false);
    }

    private void notifyLoadError() {
        if (onLoadError != null) {
            Platform.runLater(() -> onLoadError.accept(loadError));
        }
    }

    private void publishReadyBrowser() {
        synchronized (this) {
            if (disposed.get()) {
                webview.close();

                return;
            }

            ready.set(true);
        }

        if (SystemUtils.isWindows() && !Boolean.getBoolean("spark.nativeTests")) {
            ExtensionManager.instance().start(this);
        }

        Runnable task;
        while ((task = pollPending()) != null) {
            task.run();
        }
        zoomManager.apply();
        presentation.attach(false);
        applyPresentation();
        applyMuted();

        if (onReadyCallback != null) {
            onReadyCallback.run();
        }
    }

    private void finishBrowserThread() {
        var active = webview;
        extraPageOpen = false;

        try {
            if (active != null) {
                active.destroy();
            }
        } catch (Throwable failure) {
            log.error("Could not dispose the native browser", failure);
        }

        Runnable complete;
        synchronized (this) {
            ready.set(false);
            presentation.setNativeHandle(0L);
            webview = null;
            isStarting.set(false);
            complete = shutdownCallback;
            shutdownCallback = null;
        }

        if (complete != null) {
            complete.run();
        }

        if (restartRequested && !disposed.get()) {
            restartRequested = false;
            startWebviewThread(navigator.getCurrentUrl());
        }
    }

    private void handleProcessFailure(int kind) {
        if (disposed.get()) {
            return;
        }
        // Auxiliary/subframe failures do not justify replacing the main page or
        // its draft. Report them without interfering with runtime recovery.

        if (kind != 0 && kind != 1 && kind != 2) {
            log.warn("WebView2 reported a recoverable subprocess failure (kind={})", kind);

            return;
        }

        processFailed = true;
        recreateOnRetry = kind == 0 || kind == 2;
        navigationId = -1;
        loadError = switch (kind) {
            case 0 -> "The browser process stopped. Retry reopens this site. Unsent text on" +
                    " affected pages may be lost.";
            case 2 -> "This page is not responding. Retry reopens this site; unsent text may" + " be lost.";
            default -> "This page stopped unexpectedly. Retry reloads this site; unsent text" +
                    " on this page may be lost.";
        };
        log.warn("WebView2 process failure requires user recovery (kind={})", kind);
        loadingChanged(false);

        if (onLoadError != null) {
            Platform.runLater(() -> onLoadError.accept(loadError));
        }
    }

    private synchronized Runnable pollPending() {
        return pending.poll();
    }

    public synchronized void dispatch(Runnable action) {
        if (disposed.get()) {
            return;
        }

        var active = webview;

        if (!ready.get() || active == null) {
            pending.add(action);

            return;
        }

        active.dispatch(() -> {
            if (!disposed.get() && active == webview) {
                try {
                    action.run();
                } catch (Exception failure) {
                    log.error("Browser task failed", failure);
                }
            }
        });
    }

    public void eval(String js) {
        var active = webview;

        if (active != null && ready.get()) {
            active.dispatch(() -> {
                if (!disposed.get() && active == webview) {
                    active.eval(js);
                }
            });
        }
    }

    public void loadURL(String url) {
        dispatch(() -> webview.loadURL(url));
    }

    public void updateBounds(int x, int y, int width, int height) {
        presentation.updateBounds(x, y, width, height, ready.get());
        refreshResourcePolicy();
    }

    public void goBack() {
        dispatch(() -> {
            if (SystemUtils.isWindows()) {
                webview.windows().back();
            } else {
                webview.eval("history.back()");
            }
        });
    }

    public CompletableFuture<List<BrowserExtension>> listExtensions() {
        return extensionOperation(core -> new BrowserExtensions(core).list(), EXTENSION_TIMEOUT_SECONDS);
    }

    public CompletableFuture<Integer> extensionContextWindow() {
        return extensionOperation(this::activeWindowId, CONTEXT_WINDOW_TIMEOUT_SECONDS);
    }

    /** The browser engine version, such as 157.0.4341.42, sent to extension update services. */
    public CompletableFuture<String> browserVersion() {
        return extensionOperation(core -> new BrowserDevTools(core).send("Browser.getVersion", Map.of())
                .thenApply(value -> {
                    var product = value.path("product").asText("");
                    var version = product.substring(product.indexOf('/') + 1);
                    if (!version.matches("[0-9.]+")) {
                        throw new IllegalStateException("The browser version is unavailable");
                    }
                    return version;
                }), CONTEXT_WINDOW_TIMEOUT_SECONDS);
    }

    public CompletableFuture<BrowserExtension> installExtension(Path directory) {
        return extensionOperation(core -> new BrowserExtensions(core).install(directory), EXTENSION_TIMEOUT_SECONDS);
    }

    public CompletableFuture<Void> setExtensionEnabled(String id, boolean enabled) {
        return extensionOperation(core -> new BrowserExtensions(core).setEnabled(id, enabled), EXTENSION_TIMEOUT_SECONDS);
    }

    public CompletableFuture<Void> removeExtension(String id) {
        return extensionOperation(core -> new BrowserExtensions(core).remove(id), EXTENSION_TIMEOUT_SECONDS);
    }

    private CompletableFuture<Integer> activeWindowId(Pointer core) {
        return new BrowserDevTools(core).send("Browser.getWindowForTarget", Map.of()).thenApply(value -> {
            var windowId = value.path("windowId");
            if (!windowId.canConvertToInt()) {
                throw new IllegalStateException("The active browser window is unavailable");
            }

            return windowId.asInt();
        });
    }

    /** Runs a COM operation on the browser thread against the visible page's core object. */
    private <T> CompletableFuture<T> extensionOperation(Function<Pointer, CompletableFuture<T>> operation, int timeoutSeconds) {
        if (!SystemUtils.isWindows() || disposed.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Extensions require an active Windows browser"));
        }

        var result = new CompletableFuture<T>();
        dispatch(() -> {
            try {
                var core = webview.windows().activeContext().getCore();
                operation.apply(core).whenComplete((value, error) -> {
                    if (error == null) {
                        result.complete(value);
                    } else {
                        result.completeExceptionally(error);
                    }
                });
            } catch (Exception error) {
                result.completeExceptionally(error);
            }
        });

        return result.orTimeout(timeoutSeconds, TimeUnit.SECONDS);
    }

    public void closeExtraPage() {
        dispatch(() -> {
            if (SystemUtils.isWindows()) {
                webview.windows().closeExtraPage();
            }
        });
    }

    public void hibernate() {
        presentation.hibernate();
        refreshResourcePolicy();
        scheduleLowMemory();
    }

    /** Checks again once the page has been hidden long enough to give memory back. */
    private void scheduleLowMemory() {
        var delay = BrowserPresentation.LOW_MEMORY_DELAY.plusSeconds(1).toMillis();
        CompletableFuture.delayedExecutor(delay, TimeUnit.MILLISECONDS).execute(() -> {
            if (!disposed.get()) {
                refreshResourcePolicy();
            }
        });
    }

    public void wakeup(long parentHandle) {
        presentation.wakeup(parentHandle, pagePresentable && loadError == null);

        if (disposed.get() || !SystemUtils.isWindows()) {
            return;
        }

        if (!ready.get() || presentation.getNativeHandle() == 0 || webview == null) {
            startWebviewThread(navigator.getCurrentUrl());
        } else {
            presentation.reattach();
            refreshResourcePolicy();
        }
    }

    private void applyPresentation() {
        if (!disposed.get() && !(processFailed && recreateOnRetry) && SystemUtils.isWindows() && webview != null) {
            webview.windows().setBackgroundMemoryReduction(appPreferences.isBackgroundMemoryReductionEnabled());
            presentation.apply(webview, disposed::get);
        }
    }

    public void refreshResourcePolicy() {
        // Startup reads the latest request without growing its pending queue.
        if (ready.get()) {
            dispatch(this::applyPresentation);
        }
    }

    public synchronized void shutdown(Runnable onComplete) {
        if (disposed.getAndSet(true)) {
            if (isStarting.get()) {
                var previous = shutdownCallback;
                shutdownCallback = () -> {
                    try {
                        if (previous != null) {
                            previous.run();
                        }
                    } finally {
                        onComplete.run();
                    }
                };
            } else {
                onComplete.run();
            }

            return;
        }

        pending.clear();
        navigator.shutdown();

        if (isStarting.get()) {
            shutdownCallback = onComplete;

            var active = webview;

            if (active != null && ready.get()) {
                active.dispatch(active::close);
            }
            // Startup checks disposed before publishing readiness and closes itself.
        } else {
            onComplete.run();
        }
    }

    private void applyZoomFactor(double factor) {
        if (ready.get()) {
            dispatch(() -> renderZoom(factor));
        }
    }

    private void renderZoom(double factor) {
        if (SystemUtils.isWindows()) {
            webview.windows().setZoomFactor(factor);
        } else {
            webview.eval(String.format(Locale.ROOT, "document.documentElement.style.zoom='%.4f';", factor));
        }
    }

    /** CSS zoom belongs to each document. The native zoom on Windows survives navigation. */
    private void reapplyDocumentZoom() {
        if (!SystemUtils.isWindows()) {
            zoomManager.apply();
        }
    }

    public void refreshZoom() {
        zoomManager.refresh();
    }

    /** Mutes or unmutes the site, now and after the browser restarts. */
    public void setMuted(boolean value) {
        if (muted == value) {
            return;
        }

        muted = value;

        // Startup applies the latest value once the browser is ready.
        if (ready.get()) {
            dispatch(this::applyMuted);
        }
    }

    /** Shows the site's mobile or desktop version; a page already open is loaded again in that version. */
    public void setMobile(boolean value) {
        if (mobile == value) {
            return;
        }

        mobile = value;

        // Startup applies the latest value before the first page loads.
        if (ready.get()) {
            dispatch(this::applyMobile);
        }
    }

    private void applyMobile() {
        if (SystemUtils.isWindows() && webview != null) {
            webview.windows().setMobile(mobile);
            webview.windows().reloadPage();
        }
    }

    private void applyMuted() {
        if (SystemUtils.isWindows() && webview != null) {
            webview.windows().setMuted(muted);
        }
    }

    public void setZoomCallback(Consumer<Double> callback) {
        zoomManager.setZoomCallback(callback);
    }

    public double getZoomPercent() {
        return zoomManager.getZoomPercent();
    }

    public void zoomIn() {
        zoomManager.handleZoomCommand("up");
    }

    public void zoomOut() {
        zoomManager.handleZoomCommand("down");
    }

    public void setOnUrlChanged(Consumer<String> callback) {
        navigator.setOnUrlChanged(callback);
    }

    public boolean isExternalPage(String url) {
        return navigator.isExternalPage(url);
    }

    public boolean isExternalPage() {
        return navigator.isExternalPage();
    }

    /** Whether the page on screen was opened by the site or belongs to another site. */
    public boolean isOffSite() {
        return extraPageOpen || navigator.isExternalPage();
    }

    /** The address of the page on screen, or null while a page the site opened has not reported one. */
    public String getPageUrl() {
        return navigator.getPageUrl();
    }

    /** The title and site name of the page on screen; empty when they cannot be read in time. */
    public CompletableFuture<PageNames> pageNames() {
        return extensionOperation(PageNames::read, PAGE_NAMES_TIMEOUT_SECONDS).exceptionally(error -> {
            log.warn("Could not read the page's names: {}", error.getMessage());

            return PageNames.NONE;
        });
    }

    /** Leaves a page that is not on the site: closes a page the site opened, or opens the last page on the site. */
    public void returnToSite() {
        if (extraPageOpen) {
            closeExtraPage();
        } else if (navigator.isExternalPage()) {
            navigator.returnToSitePage();
        }
    }

    /** The address of the page this browser shows, or {@code about:blank} before the first page. */
    public String getCurrentUrl() {
        return navigator.getCurrentUrl();
    }

    public void setCurrentConfig(AiConfig config, String targetUrl) {
        zoomManager.showSite(config.id());
        navigator.setCurrentConfig(config, targetUrl);
    }

    public void clearBrowsingData(Consumer<Boolean> completed) {
        dispatch(() -> {
            try {
                webview.windows().clearBrowsingData(success -> Platform.runLater(() -> completed.accept(success)));
            } catch (Exception failure) {
                log.error("Could not clear browser data", failure);
                Platform.runLater(() -> completed.accept(false));
            }
        });
    }

    public void stopLoading() {
        dispatch(() -> {
            if (SystemUtils.isWindows()) {
                webview.windows().stop();
            }
        });
    }

    public void focusBrowser() {
        focusBrowser(null);
    }

    /**
     * Moves keyboard focus into the page once it is on screen. A request made before the page was
     * hidden again is dropped; showing it again does not cancel the request.
     */
    public void focusBrowser(Consumer<Boolean> completed) {
        dispatch(() -> {
            if (presentation.isDisplayed() && !processFailed && pagePresentable && loadError == null &&
                    SystemUtils.isWindows()) {
                boolean accepted = webview.windows().focus();

                if (!accepted) {
                    log.debug("Focus skipped because the host is changing visibility");
                }

                if (completed != null) {
                    completed.accept(accepted);
                }
            }
        });
    }

    public void reload() {
        if (disposed.get()) {
            return;
        }

        if (!isStarting.get() && !disposed.get()) {
            synchronized (this) {
                pending.clear();
            }
            loadError = null;
            loadingChanged(true);
            startWebviewThread(navigator.getCurrentUrl());
        } else {
            loadError = null;
            loadingChanged(true);
            dispatch(() -> {
                if (recreateOnRetry) {
                    restartRequested = true;
                    webview.close();
                } else {
                    processFailed = false;
                    webview.loadURL(navigator.getCurrentUrl());
                }
            });
        }
    }

    public void resetZoom() {
        zoomManager.resetZoom();
    }

    public void setVisible(boolean visible) {
        presentation.requestVisible(visible);
        refreshResourcePolicy();
    }
}
