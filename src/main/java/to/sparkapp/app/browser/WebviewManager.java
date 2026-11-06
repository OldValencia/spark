package to.sparkapp.app.browser;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import javafx.application.Platform;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import to.sparkapp.app.config.AiConfiguration.AiConfig;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.utils.SystemUtils;

@Slf4j
public class WebviewManager {

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
    private volatile String loadError;
    private volatile boolean processFailed;
    private volatile boolean recreateOnRetry;
    private volatile boolean restartRequested;
    private long navigationId;
    @Setter
    private Consumer<Boolean> onLoadingChanged;
    @Setter
    private Consumer<String> onLoadError;

    private void loadingChanged(boolean value) {
        loading = value;

        if (onLoadingChanged != null) {
            Platform.runLater(() -> onLoadingChanged.accept(value));
        }
    }

    @Setter
    private Runnable onReadyCallback;

    public WebviewManager(AppPreferences appPreferences) {
        this.appPreferences = appPreferences;
        this.presentation = new BrowserPresentation(appPreferences);
        this.zoomManager = new WebviewZoomManager(appPreferences, this);
        this.navigator = new WebviewNavigator(this, zoomManager);
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
        browser.setPresentation(false,
                presentation.isHibernated() && appPreferences.isBackgroundMemoryReductionEnabled());
        browser.observeProcessFailures(this::handleProcessFailure);
        browser.observe(this::navigationStarting, this::navigationCompleted);
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
        zoomManager.applyZoomCss();

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

        Runnable task;
        while ((task = pollPending()) != null) {
            task.run();
        }
        zoomManager.applyZoomCss();
        presentation.attach(false);
        applyPresentation();

        if (onReadyCallback != null) {
            onReadyCallback.run();
        }
    }

    private void finishBrowserThread() {
        var active = webview;

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
    }

    public void hibernate() {
        presentation.hibernate();
        refreshResourcePolicy();
    }

    public void wakeup(long parentHandle) {
        presentation.wakeup(parentHandle, !loading && loadError == null);

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

    public void setCurrentConfig(AiConfig config, String targetUrl) {
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

    public void focusBrowser(Consumer<Boolean> completed) {
        long generation = presentation.getGeneration();
        dispatch(() -> {
            if (generation == presentation.getGeneration() && !processFailed && !presentation.isHibernated() &&
                    !loading && loadError == null && SystemUtils.isWindows()) {
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
