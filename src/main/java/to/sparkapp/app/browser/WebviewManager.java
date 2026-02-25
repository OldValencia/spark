package to.sparkapp.app.browser;

import javafx.application.Platform;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import to.sparkapp.app.config.AiConfiguration;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.utils.NativeWindowUtils;
import to.sparkapp.app.utils.SystemUtils;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Slf4j
public class WebviewManager {

    private volatile Webview webview;
    private volatile long nativeHandle = 0L;
    private volatile long parentHandle = 0L;

    private final AtomicBoolean ready = new AtomicBoolean(false);
    private final AtomicBoolean disposed = new AtomicBoolean(false);

    private volatile int retryCount = 0;
    private static final int MAX_RETRIES = 10;
    private volatile int nativeX, nativeY, nativeW, nativeH;

    // --- Sub-Managers ---
    private final WebviewZoomManager zoomManager;
    private final WebviewNavigator navigator;

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
        if (disposed.get()) return;

        this.parentHandle = parentHandle;
        this.nativeX = x;
        this.nativeY = y;
        this.nativeW = width;
        this.nativeH = height;
        NativeWindowUtils.cachedWebviewHeight = height;

        startWebviewThread(startUrl);
    }

    private void startWebviewThread(String initialUrl) {
        if (retryCount >= MAX_RETRIES || disposed.get()) {
            log.error("NativeWebViewBridge: Max retries reached or disposed. Aborting start.");
            return;
        }

        var webviewThread = new Thread(() -> {
            try {
                log.info("NativeWebViewBridge: Starting webview event loop (Attempt {}/{})", retryCount + 1, MAX_RETRIES);
                ready.set(false);

                webview = new Webview(false);
                this.nativeHandle = webview.getNativeWindowPointer();

                if (nativeHandle != 0) {
                    NativeWindowUtils.setBounds(nativeHandle, -15000, -15000, 10, 10);
                    NativeWindowUtils.setVisible(nativeHandle, false);
                    if (parentHandle != 0) {
                        NativeWindowUtils.setParent(nativeHandle, parentHandle);
                    }
                }

                setupJsApi();
                webview.setInitScript(INIT_SCRIPTS);
                webview.setSize(nativeW, nativeH);

                webview.loadURL(initialUrl != null ? initialUrl : "about:blank");

                webview.dispatch(() -> {
                    zoomManager.applyZoomCss();
                    ready.set(true);
                    retryCount = 0;
                    log.info("NativeWebViewBridge: Webview is completely READY.");

                    if (onReadyCallback != null) onReadyCallback.run();
                });

                webview.run(); // Blocking native loop

                if (!disposed.get()) throw new RuntimeException("Webview event loop exited unexpectedly");

            } catch (Throwable t) {
                log.error("CRITICAL: Webview crashed!", t);
                ready.set(false);
                this.nativeHandle = 0L;
                retryCount++;
                webview = null;

                if (retryCount < MAX_RETRIES && !disposed.get()) {
                    try {
                        Thread.sleep(1500);
                    } catch (InterruptedException ignored) {
                    }
                    startWebviewThread(initialUrl);
                }
            }
        }, "spark-webview-thread-" + System.currentTimeMillis());

        webviewThread.setDaemon(true);
        webviewThread.start();
    }

    private void setupJsApi() {
        var api = new SparkJsApi(webview);

        api.on("zoom", args -> {
            if (!args.isEmpty()) zoomManager.handleZoomCommand(args.get(0).getAsString());
        });

        api.on("urlChanged", args -> {
            if (!args.isEmpty()) navigator.handleUrlChange(args.get(0).getAsString());
        });
    }

    public void dispatch(Runnable action) {
        if (disposed.get() || retryCount >= MAX_RETRIES) return;

        if (ready.get() && webview != null) {
            webview.dispatch(() -> {
                try {
                    action.run();
                } catch (Exception e) {
                    log.error("WebviewManager: Task failed", e);
                }
            });
        } else {
            new Thread(() -> {
                int waits = 0;
                while (!ready.get() && !disposed.get() && retryCount < MAX_RETRIES) {
                    try {
                        Thread.sleep(50);
                        waits++;
                        if (waits > 100) return;
                    } catch (InterruptedException ignored) {
                        return;
                    }
                }

                if (webview != null && ready.get()) {
                    webview.dispatch(() -> {
                        try {
                            action.run();
                        } catch (Exception e) {
                            log.error("WebviewManager: Task failed", e);
                        }
                    });
                }
            }, "webview-dispatch-waiter-" + System.currentTimeMillis()).start();
        }
    }

    public void eval(String js) {
        if (webview != null && ready.get()) webview.eval(js);
    }

    public void loadURL(String url) {
        if (webview != null) webview.loadURL(url);
    }

    public void updateBounds(int x, int y, int width, int height) {
        this.nativeX = x;
        this.nativeY = y;
        this.nativeW = width;
        this.nativeH = height;
        NativeWindowUtils.cachedWebviewHeight = height;

        if (!ready.get()) return;

        dispatch(() -> {
            if (webview != null) webview.setSize(width, height);
        });
        if (nativeHandle != 0) NativeWindowUtils.setBounds(nativeHandle, x, y, width, height);
    }

    public void hibernate() {
        if (!ready.get() || nativeHandle == 0 || !SystemUtils.isWindows()) return;
        NativeWindowUtils.setVisible(nativeHandle, false);
        NativeWindowUtils.setBounds(nativeHandle, -15000, -15000, 10, 10);
    }

    public void wakeup(long parentHandle) {
        this.parentHandle = parentHandle;
        if (!ready.get() || nativeHandle == 0 || !SystemUtils.isWindows()) return;
        NativeWindowUtils.setParent(nativeHandle, parentHandle);
        NativeWindowUtils.setBounds(nativeHandle, nativeX, nativeY, nativeW, nativeH);
        NativeWindowUtils.setVisible(nativeHandle, true);
    }

    public void shutdown(Runnable onComplete) {
        if (disposed.getAndSet(true)) return;
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

    // --- Delegation Methods (To keep external API identical) ---
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
        if (nativeHandle != 0) NativeWindowUtils.setVisible(nativeHandle, visible);
    }
}