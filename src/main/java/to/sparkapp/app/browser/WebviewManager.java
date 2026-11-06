package to.sparkapp.app.browser;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import to.sparkapp.app.config.AiConfiguration;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.utils.NativeWindowUtils;
import to.sparkapp.app.utils.SystemUtils;
import to.sparkapp.app.utils.UrlUtils;

import javafx.application.Platform;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Slf4j
public class WebviewManager {

    private final Object presentationLock = new Object();
    private final AppPreferences appPreferences;
    private volatile boolean displayRequested;
    private Runnable shutdownCallback;
    private volatile Webview webview;
    private volatile long nativeHandle = 0L;
    private volatile long parentHandle = 0L;

    private final AtomicBoolean ready = new AtomicBoolean(false);
    private final AtomicBoolean disposed = new AtomicBoolean(false);
    private final AtomicBoolean isStarting = new AtomicBoolean(false);
    private volatile boolean isHibernated = false;
    private volatile long visibilityGeneration;

    private volatile int nativeX;
    private volatile int nativeY;
    private volatile int nativeW;
    private volatile int nativeH;

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
    @Setter private Consumer<Boolean> onLoadingChanged;
    @Setter private Consumer<String> onLoadError;

    private void loadingChanged(boolean value) {
        loading = value;
        if (onLoadingChanged != null) Platform.runLater(() -> onLoadingChanged.accept(value));
    }

    @Setter
    private Runnable onReadyCallback;

    private static final String INIT_SCRIPTS = """
            (function() {
                document.addEventListener('wheel', function(e) {
                    if (!e.ctrlKey) return;
                    e.preventDefault();
                    window.sparkCall('zoom', e.deltaY > 0 ? 'down' : 'up');
                }, {passive: false});
            
                document.addEventListener('keydown', function(e) {
                    // Block Alt + Left / Alt + Right (Browser Back/Forward)
                    if (e.altKey && (e.key === 'ArrowLeft' || e.key === 'ArrowRight')) {
                        e.preventDefault();
                        return;
                    }

                    if (!e.ctrlKey) return;

                    // Zoom with support for non-English layouts using e.code
                    if (e.code === 'Equal' || e.code === 'NumpadAdd' || e.key === '=' || e.key === '+') {
                        e.preventDefault(); window.sparkCall('zoom', 'up');
                    } else if (e.code === 'Minus' || e.code === 'NumpadSubtract' || e.key === '-') {
                        e.preventDefault(); window.sparkCall('zoom', 'down');
                    } else if (e.code === 'Digit0' || e.code === 'Numpad0' || e.key === '0') {
                        e.preventDefault(); window.sparkCall('zoom', 'reset');
                    }
                });
            
                document.addEventListener('click', function(e) {
                    var a = e.target.closest('a');
                    if (a && a.href && (a.href.startsWith('http://') || a.href.startsWith('https://'))) {
                        try {
                            var currentHost = window.location.hostname;
                            var targetHost = new URL(a.href).hostname;

                            if (currentHost !== targetHost || a.target === '_blank') {
                                e.preventDefault();
                                window.sparkCall('linkClicked', a.href);
                            }
                        } catch (err) {
                            console.error('SparkLinkIntercept Error:', err);
                        }
                    }
                });
            
                document.addEventListener('auxclick', function(e) {
                    if (e.button === 1) { // Middle mouse button
                        var a = e.target.closest('a');
                        if (a && a.href && (a.href.startsWith('http://') || a.href.startsWith('https://'))) {
                            e.preventDefault();
                            window.sparkCall('middleClicked', a.href);
                        }
                    }
                });
            
                document.addEventListener('DOMContentLoaded', function() {
                    window.sparkCall('documentReady', window.location.href);
                }, {once: true});
                window.sparkCall('urlChanged', window.location.href);
                var _push = history.pushState;
                history.pushState = function() { _push.apply(this, arguments); window.sparkCall('urlChanged', window.location.href); };
                var _replace = history.replaceState;
                history.replaceState = function() { _replace.apply(this, arguments); window.sparkCall('urlChanged', window.location.href); };
                window.addEventListener('popstate', function() { window.sparkCall('urlChanged', window.location.href); });
                window.addEventListener('hashchange', function() { window.sparkCall('urlChanged', window.location.href); });
            })();
            """;

    public WebviewManager(AppPreferences appPreferences) {
        this.appPreferences = appPreferences;
        this.zoomManager = new WebviewZoomManager(appPreferences, this);
        this.navigator = new WebviewNavigator(this, zoomManager);
    }

    public void init(String startUrl, long parentHandle, int x, int y, int width, int height) {
        if (disposed.get()) {
            return;
        }

        this.parentHandle = parentHandle;
        this.nativeX = x;
        this.nativeY = y;
        this.nativeW = width;
        this.nativeH = height;

        startWebviewThread(startUrl);
    }

    private synchronized void startWebviewThread(String initialUrl) {
        if (disposed.get() || !isStarting.compareAndSet(false, true)) {
            return;
        }

        var webviewThread = new Thread(() -> {
            try {
                ready.set(false);
                processFailed = false;
                recreateOnRetry = false;

                // Always let the library create its own top-level window and reparent it
                // afterwards: the embedded (parent handle) path of the native library
                // reports the host window as its own window, so every later setBounds /
                // setVisible call would move or hide the whole application window.
                webview = new Webview(false);
                this.nativeHandle = webview.getNativeWindowPointer();

                if (nativeHandle != 0) {
                    if (isHibernated) {
                        NativeWindowUtils.setVisible(nativeHandle, false);
                        NativeWindowUtils.unparent(nativeHandle);
                    } else if (parentHandle != 0) {
                        NativeWindowUtils.setParent(nativeHandle, parentHandle);
                        NativeWindowUtils.setVisible(nativeHandle, false);
                        if (nativeW > 0 && nativeH > 0) {
                            NativeWindowUtils.setBounds(nativeHandle, nativeX, nativeY, nativeW, nativeH);
                        }
                    }
                }

                if (SystemUtils.isWindows()) {
                    webview.windows().setPresentation(false, isHibernated && appPreferences.isBackgroundMemoryReductionEnabled());
                    webview.windows().observeProcessFailures(this::handleProcessFailure);
                    webview.windows().observe(id -> {
                        if (processFailed || disposed.get()) return;
                        navigationId = id;
                        loadError = null;
                        loadingChanged(true);
                    }, (id, success) -> {
                        if (processFailed || disposed.get() || id != navigationId) return;
                        var url = webview.windows().source();
                        if ("about:blank".equals(url)) return;
                        Platform.runLater(() -> navigator.handleUrlChange(url));
                        zoomManager.applyZoomCss();
                        if (!success) {
                            loadError = "This page could not be loaded. Check your connection and try again.";
                            if (onLoadError != null) Platform.runLater(() -> onLoadError.accept(loadError));
                        }
                        loadingChanged(false);
                    });
                }
                setupJsApi();
                webview.setInitScript(INIT_SCRIPTS);

                var urlToLoad = initialUrl != null ? initialUrl : "about:blank";
                synchronized (this) {
                    if (pending.isEmpty()) webview.loadURL(urlToLoad);
                }

                webview.dispatch(() -> {
                    synchronized (this) {
                        if (disposed.get()) {
                            webview.close();
                            return;
                        }
                        ready.set(true);
                    }
                    Runnable task;
                    while ((task = pollPending()) != null) task.run();
                    zoomManager.applyZoomCss();

                    if (nativeHandle != 0) {
                        if (isHibernated) {
                            NativeWindowUtils.setVisible(nativeHandle, false);
                            NativeWindowUtils.unparent(nativeHandle);
                        } else if (parentHandle != 0) {
                            NativeWindowUtils.setParent(nativeHandle, parentHandle);
                            NativeWindowUtils.setBounds(nativeHandle, nativeX, nativeY, nativeW, nativeH);
                        }
                    }

                    applyPresentation();
                    if (onReadyCallback != null) {
                        onReadyCallback.run();
                    }
                });

                webview.run();

            } catch (Throwable t) {
                log.error("WebviewManager: Webview thread encountered an error", t);
                loadError = "The browser could not start. Try opening the site again.";
                loadingChanged(false);
                if (onLoadError != null) Platform.runLater(() -> onLoadError.accept(loadError));
            } finally {
                var active = webview;
                try { if (active != null) active.destroy(); }
                catch (Throwable failure) { log.error("Could not dispose the native browser", failure); }
                Runnable complete;
                synchronized (this) {
                    ready.set(false);
                    nativeHandle = 0L;
                    webview = null;
                    isStarting.set(false);
                    complete = shutdownCallback;
                    shutdownCallback = null;
                }
                if (complete != null) complete.run();
                if (restartRequested && !disposed.get()) {
                    restartRequested = false;
                    startWebviewThread(navigator.getCurrentUrl());
                }
            }
        }, "spark-webview-thread-" + System.currentTimeMillis());

        webviewThread.setDaemon(true);
        webviewThread.start();
    }

    private void setupJsApi() {
        if (webview == null) {
            return;
        }

        var api = new SparkJsApi(webview);

        api.on("documentReady", args -> {
            if (!SystemUtils.isWindows()) loadingChanged(false);
        });
        api.on("zoom", args -> {
            if (!args.isEmpty()) {
                zoomManager.handleZoomCommand(args.get(0).asText());
            }
        });

        api.on("urlChanged", args -> {
            if (!args.isEmpty()) {
                navigator.handleUrlChange(args.get(0).asText());
            }
        });

        api.on("linkClicked", args -> {
            if (!args.isEmpty()) {
                navigator.handleLinkClicked(args.get(0).asText());
            }
        });

        api.on("middleClicked", args -> {
            if (!args.isEmpty()) {
                UrlUtils.openLink(args.get(0).asText());
            }
        });
    }

    private void handleProcessFailure(int kind) {
        if (disposed.get()) return;
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
            case 0 -> "The browser process stopped. Retry reopens this site. Unsent text on affected pages may be lost.";
            case 2 -> "This page is not responding. Retry reopens this site; unsent text may be lost.";
            default -> "This page stopped unexpectedly. Retry reloads this site; unsent text on this page may be lost.";
        };
        log.warn("WebView2 process failure requires user recovery (kind={})", kind);
        loadingChanged(false);
        if (onLoadError != null) Platform.runLater(() -> onLoadError.accept(loadError));
    }

    private synchronized Runnable pollPending() { return pending.poll(); }

    public synchronized void dispatch(Runnable action) {
        if (disposed.get()) return;
        var active = webview;
        if (!ready.get() || active == null) {
            pending.add(action);
            return;
        }
        active.dispatch(() -> {
            if (!disposed.get() && active == webview) {
                try { action.run(); }
                catch (Exception failure) { log.error("Browser task failed", failure); }
            }
        });
    }

    public void eval(String js) {
        var active = webview;
        if (active != null && ready.get()) {
            active.dispatch(() -> {
                if (!disposed.get() && active == webview) active.eval(js);
            });
        }
    }

    public void loadURL(String url) {
        dispatch(() -> webview.loadURL(url));
    }

    public void updateBounds(int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        this.nativeX = x;
        this.nativeY = y;
        this.nativeW = width;
        this.nativeH = height;

        if (!ready.get()) {
            return;
        }

        if (nativeHandle != 0 && !isHibernated) {
            // Bounds are physical pixels, so they are applied with SetWindowPos rather
            // than webview_set_size(): the native call treats its arguments as 96 DPI
            // units and multiplies them by the monitor scale, which would blow the
            // window up by the scale factor on a HiDPI monitor. Resizing the window
            // makes the library lay out its widget through WM_SIZE anyway.
            if (parentHandle != 0 && !NativeWindowUtils.isChildOf(nativeHandle, parentHandle)) {
                NativeWindowUtils.setParent(nativeHandle, parentHandle);
            }
            NativeWindowUtils.setBounds(nativeHandle, x, y, width, height);
        }
    }

    public void hibernate() {
        synchronized (presentationLock) {
            visibilityGeneration++;
            isHibernated = true;
            displayRequested = false;
        }
        if (SystemUtils.isWindows() && nativeHandle != 0) {
            NativeWindowUtils.setVisible(nativeHandle, false);
            // Detach synchronously before JavaFX can destroy its HWND. Keep the viewport.
            NativeWindowUtils.unparent(nativeHandle);
        }
        refreshResourcePolicy();
    }

    public void wakeup(long parentHandle) {
        synchronized (presentationLock) {
            visibilityGeneration++;
            if (parentHandle != 0) this.parentHandle = parentHandle;
            isHibernated = false;
            displayRequested = !loading && loadError == null;
        }
        if (disposed.get() || !SystemUtils.isWindows()) return;
        if (!ready.get() || nativeHandle == 0 || webview == null) {
            startWebviewThread(navigator.getCurrentUrl());
        } else {
            if (!NativeWindowUtils.isChildOf(nativeHandle, this.parentHandle))
                NativeWindowUtils.setParent(nativeHandle, this.parentHandle);
            NativeWindowUtils.setBounds(nativeHandle, nativeX, nativeY, nativeW, nativeH);
            refreshResourcePolicy();
        }
    }

    /** Reconciles the latest desired state on the browser thread; stale shows never win. */
    private void applyPresentation() {
        if (disposed.get() || (processFailed && recreateOnRetry) || !SystemUtils.isWindows() || webview == null) return;
        long generation;
        boolean show;
        boolean lowMemory;
        synchronized (presentationLock) {
            generation = visibilityGeneration;
            show = displayRequested && !isHibernated;
            lowMemory = isHibernated && appPreferences.isBackgroundMemoryReductionEnabled();
        }
        webview.windows().setPresentation(show, lowMemory);
        synchronized (presentationLock) {
            if (generation != visibilityGeneration || disposed.get()) return;
            if (nativeHandle != 0) NativeWindowUtils.setVisible(nativeHandle, show);
        }
    }

    public void refreshResourcePolicy() {
        // Startup reads the latest flags, so visibility changes need not grow its work queue.
        if (ready.get()) dispatch(this::applyPresentation);
    }

    public synchronized void shutdown(Runnable onComplete) {
        if (disposed.getAndSet(true)) {
            if (isStarting.get()) {
                var previous = shutdownCallback;
                shutdownCallback = () -> {
                    try { if (previous != null) previous.run(); }
                    finally { onComplete.run(); }
                };
            } else onComplete.run();
            return;
        }

        pending.clear();
        navigator.shutdown();
        if (isStarting.get()) {
            shutdownCallback = onComplete;
            var active = webview;
            if (active != null && ready.get()) active.dispatch(active::close);
            // Startup checks disposed before publishing readiness and closes itself.
        } else {
            onComplete.run();
        }
    }

    public void setZoomCallback(Consumer<Double> callback) {
        zoomManager.setZoomCallback(callback);
    }

    public double getZoomPercent() { return zoomManager.getZoomPercent(); }
    public void zoomIn() { zoomManager.handleZoomCommand("up"); }
    public void zoomOut() { zoomManager.handleZoomCommand("down"); }

    public void setOnUrlChanged(Consumer<String> callback) {
        navigator.setOnUrlChanged(callback);
    }

    public void setCurrentConfig(AiConfiguration.AiConfig config, String targetUrl) {
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
        dispatch(() -> { if (SystemUtils.isWindows()) webview.windows().stop(); });
    }

    public void focusBrowser() { focusBrowser(null); }

    public void focusBrowser(Consumer<Boolean> completed) {
        long generation = visibilityGeneration;
        dispatch(() -> {
            if (generation == visibilityGeneration && !processFailed && !isHibernated && !loading
                    && loadError == null && SystemUtils.isWindows()) {
                boolean accepted = webview.windows().focus();
                if (!accepted) log.debug("Focus skipped because the host is changing visibility");
                if (completed != null) completed.accept(accepted);
            }
        });
    }

    public void reload() {
        if (disposed.get()) return;
        if (!isStarting.get() && !disposed.get()) {
            synchronized (this) { pending.clear(); }
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
        synchronized (presentationLock) {
            visibilityGeneration++;
            displayRequested = visible;
        }
        if (!visible && nativeHandle != 0) NativeWindowUtils.setVisible(nativeHandle, false);
        refreshResourcePolicy();
    }
}
