package to.sparkapp.app.browser;

import javafx.application.Platform;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import to.sparkapp.app.config.AiConfiguration;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.utils.NativeWindowUtils;
import to.sparkapp.app.utils.SystemUtils;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Slf4j
public class WebviewManager {

    private volatile Webview webview;
    private volatile long nativeHandle = 0L;
    private volatile long parentHandle = 0L;

    private final AtomicBoolean ready = new AtomicBoolean(false);
    private final AtomicBoolean disposed = new AtomicBoolean(false);
    private final AtomicBoolean isStarting = new AtomicBoolean(false);
    private volatile boolean isFirstStart = true;
    private volatile boolean isHibernated = false;

    private volatile int nativeX;
    private volatile int nativeY;
    private volatile int nativeW;
    private volatile int nativeH;

    private final WebviewZoomManager zoomManager;
    private final WebviewNavigator navigator;

    private final ScheduledExecutorService dispatchWaitScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        var t = new Thread(r, "webview-dispatch-waiter");
        t.setDaemon(true);
        return t;
    });

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
                    if (!e.ctrlKey) return;
                    if (e.key === '=' || e.key === '+') { e.preventDefault(); window.sparkCall('zoom', 'up'); }
                    if (e.key === '-')                  { e.preventDefault(); window.sparkCall('zoom', 'down'); }
                    if (e.key === '0')                  { e.preventDefault(); window.sparkCall('zoom', 'reset'); }
                });
            
                window.sparkCall('urlChanged', window.location.href);
                var _push = history.pushState;
                history.pushState = function() { _push.apply(this, arguments); window.sparkCall('urlChanged', window.location.href); };
                var _replace = history.replaceState;
                history.replaceState = function() { _replace.apply(this, arguments); window.sparkCall('urlChanged', window.location.href); };
                window.addEventListener('popstate', function() { window.sparkCall('urlChanged', window.location.href); });
            })();
            """;

    public WebviewManager(AppPreferences appPreferences) {
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
        NativeWindowUtils.cachedWebviewHeight = height;

        startWebviewThread(startUrl);
    }

    private void startWebviewThread(String initialUrl) {
        if (disposed.get() || !isStarting.compareAndSet(false, true)) {
            return;
        }

        var webviewThread = new Thread(() -> {
            try {
                ready.set(false);

                var firstStart = isFirstStart;
                isFirstStart = false;

                webview = firstStart ? new Webview(false, parentHandle) : new Webview(false);
                this.nativeHandle = webview.getNativeWindowPointer();

                if (nativeHandle != 0 && !firstStart) {
                    NativeWindowUtils.setVisible(nativeHandle, false);
                    NativeWindowUtils.setBounds(nativeHandle, 15000, 15000, 10, 10);
                    if (parentHandle != 0) {
                        NativeWindowUtils.setParent(nativeHandle, parentHandle);
                    }
                }

                setupJsApi();
                webview.setInitScript(INIT_SCRIPTS);
                webview.setSize(nativeW, nativeH);

                var urlToLoad = initialUrl != null ? initialUrl : "about:blank";
                webview.loadURL(urlToLoad);

                webview.dispatch(() -> {
                    zoomManager.applyZoomCss();
                    ready.set(true);

                    if (nativeHandle != 0 && parentHandle != 0 && !isHibernated) {
                        NativeWindowUtils.setParent(nativeHandle, parentHandle);
                        NativeWindowUtils.setBounds(nativeHandle, nativeX, nativeY, nativeW, nativeH);
                        NativeWindowUtils.setVisible(nativeHandle, true);
                    }

                    if (onReadyCallback != null) {
                        onReadyCallback.run();
                    }
                });

                webview.run();

            } catch (Throwable t) {
                log.error("WebviewManager: Webview thread encountered an error", t);
            } finally {
                ready.set(false);
                nativeHandle = 0L;
                webview = null;
                isStarting.set(false);
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

        api.on("zoom", args -> {
            if (!args.isEmpty()) {
                zoomManager.handleZoomCommand(args.get(0).getAsString());
            }
        });

        api.on("urlChanged", args -> {
            if (!args.isEmpty()) {
                navigator.handleUrlChange(args.get(0).getAsString());
            }
        });
    }

    public void dispatch(Runnable action) {
        if (disposed.get()) {
            return;
        }

        if (!ready.get() && webview == null) {
            startWebviewThread(navigator.getCurrentUrl());
        }

        if (ready.get() && webview != null) {
            webview.dispatch(() -> {
                try {
                    action.run();
                } catch (Exception e) {
                    log.error("WebviewManager: Task failed", e);
                }
            });
        } else {
            scheduleDispatchRetry(action, 0);
        }
    }

    private void scheduleDispatchRetry(Runnable action, int attempt) {
        if (disposed.get() || attempt > 100) {
            return;
        }

        dispatchWaitScheduler.schedule(() -> {
            if (disposed.get()) {
                return;
            }
            if (ready.get() && webview != null) {
                webview.dispatch(() -> {
                    try {
                        action.run();
                    } catch (Exception e) {
                        log.error("WebviewManager: Deferred task failed", e);
                    }
                });
            } else {
                scheduleDispatchRetry(action, attempt + 1);
            }
        }, 50, TimeUnit.MILLISECONDS);
    }

    public void eval(String js) {
        if (webview != null && ready.get()) {
            webview.eval(js);
        }
    }

    public void loadURL(String url) {
        if (webview != null) {
            webview.loadURL(url);
        }
    }

    public void updateBounds(int x, int y, int width, int height) {
        this.nativeX = x;
        this.nativeY = y;
        this.nativeW = width;
        this.nativeH = height;
        NativeWindowUtils.cachedWebviewHeight = height;

        if (!ready.get()) {
            return;
        }

        dispatch(() -> {
            if (webview != null) {
                webview.setSize(width, height);
            }
        });

        if (nativeHandle != 0 && !isHibernated) {
            if (parentHandle != 0) {
                NativeWindowUtils.setParent(nativeHandle, parentHandle);
            }
            NativeWindowUtils.setBounds(nativeHandle, x, y, width, height);
        }
    }

    public void hibernate() {
        isHibernated = true;

        if (!ready.get() || nativeHandle == 0 || !SystemUtils.isWindows()) {
            return;
        }

        NativeWindowUtils.setVisible(nativeHandle, false);
        NativeWindowUtils.setBounds(nativeHandle, 15000, 15000, 10, 10);
        NativeWindowUtils.unparent(nativeHandle);
    }

    public void wakeup(long parentHandle) {
        if (parentHandle != 0) {
            this.parentHandle = parentHandle;
        }

        isHibernated = false;

        if (disposed.get() || !SystemUtils.isWindows()) {
            return;
        }

        if (!ready.get() || nativeHandle == 0 || webview == null) {
            startWebviewThread(navigator.getCurrentUrl());
        } else {
            NativeWindowUtils.setParent(nativeHandle, this.parentHandle);
            NativeWindowUtils.setBounds(nativeHandle, nativeX, nativeY, nativeW, nativeH);
            NativeWindowUtils.setVisible(nativeHandle, true);
        }
    }

    public void shutdown(Runnable onComplete) {
        if (disposed.getAndSet(true)) {
            return;
        }

        dispatchWaitScheduler.shutdownNow();

        if (webview != null && ready.get()) {
            dispatch(() -> {
                try {
                    webview.close();
                } catch (Throwable ignored) {
                }
                Platform.runLater(onComplete);
            });
        } else {
            onComplete.run();
        }
    }

    public void setZoomCallback(Consumer<Double> callback) {
        zoomManager.setZoomCallback(callback);
    }

    public void setOnUrlChanged(Consumer<String> callback) {
        navigator.setOnUrlChanged(callback);
    }

    public void setCurrentConfig(AiConfiguration.AiConfig config) {
        navigator.setCurrentConfig(config);
    }

    public void navigate(String url) {
        navigator.navigate(url);
    }

    public void clearCookies() {
        navigator.clearCookies();
    }

    public void resetZoom() {
        zoomManager.resetZoom();
    }

    public void setVisible(boolean visible) {
        if (nativeHandle != 0) {
            if (visible && isHibernated) {
                return;
            }
            NativeWindowUtils.setVisible(nativeHandle, visible);
        }
    }
}
