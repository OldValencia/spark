package to.sparkapp.app.browser;

import co.casterlabs.rakurai.json.Rson;
import co.casterlabs.rakurai.json.element.JsonArray;
import javafx.application.Platform;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import to.sparkapp.app.browser.webview.Webview;
import to.sparkapp.app.config.AiConfiguration;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.utils.NativeWindowUtils;
import to.sparkapp.app.utils.SystemUtils;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

@Slf4j
public class NativeWebViewBridge {

    private volatile Webview webview;
    private volatile long nativeHandle = 0L;
    private volatile long parentHandle = 0L;

    private final AtomicBoolean ready = new AtomicBoolean(false);
    private final AtomicBoolean disposed = new AtomicBoolean(false);

    private volatile int retryCount = 0;
    private static final int MAX_RETRIES = 10;

    private volatile String currentUrl;
    private volatile double currentZoom;
    private volatile AiConfiguration.AiConfig currentConfig;

    private volatile int nativeX, nativeY, nativeW, nativeH;

    private final AppPreferences appPreferences;

    // Идентификатор текущей навигации (чтобы отменять старые при быстром клике)
    private volatile long currentNavId = 0L;

    @Setter
    private Consumer<Double> zoomCallback;
    @Setter
    private Consumer<String> onUrlChanged;
    @Setter
    private Runnable onReadyCallback;

    private static final String INIT_SCRIPTS = """
            (function() {
                document.addEventListener('wheel', function(e) {
                    if (!e.ctrlKey) return;
                    e.preventDefault();
                    window.sparkZoom(e.deltaY > 0 ? 'down' : 'up');
                }, {passive: false});
            
                document.addEventListener('keydown', function(e) {
                    if (!e.ctrlKey) return;
                    if (e.key === '=' || e.key === '+') { e.preventDefault(); window.sparkZoom('up'); }
                    if (e.key === '-')                  { e.preventDefault(); window.sparkZoom('down'); }
                    if (e.key === '0')                  { e.preventDefault(); window.sparkZoom('reset'); }
                });
            
                window.sparkUrl(window.location.href);
                var _push = history.pushState;
                history.pushState = function() { _push.apply(this, arguments); window.sparkZoom(window.location.href); };
                var _replace = history.replaceState;
                history.replaceState = function() { _replace.apply(this, arguments); window.sparkZoom(window.location.href); };
                window.addEventListener('popstate', function() { window.sparkZoom(window.location.href); });
            })();
            """;

    public NativeWebViewBridge(AppPreferences appPreferences) {
        this.appPreferences = appPreferences;
        this.currentZoom = appPreferences.getLastZoomValue();
    }

    public void init(String startUrl, long parentHandle, int x, int y, int width, int height) {
        if (disposed.get()) return;

        this.currentUrl = startUrl;
        this.parentHandle = parentHandle;
        this.nativeX = x;
        this.nativeY = y;
        this.nativeW = width;
        this.nativeH = height;

        NativeWindowUtils.cachedWebviewHeight = height;

        startWebviewThread();
    }

    private void startWebviewThread() {
        if (retryCount >= MAX_RETRIES || disposed.get()) {
            log.error("NativeWebViewBridge: Max retries reached or disposed. Aborting start.");
            return;
        }

        var webviewThread = new Thread(() -> {
            try {
                log.info("NativeWebViewBridge: Starting native webview event loop (Attempt {}/{})", retryCount + 1, MAX_RETRIES);
                ready.set(false);

                webview = new Webview(false);

                long handle = webview.getNativeWindowPointer();
                this.nativeHandle = handle;

                if (handle != 0) {
                    NativeWindowUtils.setBounds(handle, -15000, -15000, 10, 10);
                    NativeWindowUtils.setVisible(handle, false);
                    if (parentHandle != 0) {
                        NativeWindowUtils.setParent(handle, parentHandle);
                    }
                }

                bindJsFunctions();
                webview.setInitScript(INIT_SCRIPTS);
                webview.setSize(nativeW, nativeH);

                String loadUrl = currentUrl != null ? currentUrl : "about:blank";
                webview.loadURL(loadUrl);

                webview.dispatch(() -> {
                    try {
                        applyZoomCss(currentZoom);
                        ready.set(true);
                        retryCount = 0;
                        log.info("NativeWebViewBridge: Webview is completely READY.");

                        if (onReadyCallback != null) {
                            onReadyCallback.run();
                        }
                    } catch (Exception e) {
                        log.error("NativeWebViewBridge: Error in initial dispatch", e);
                    }
                });

                log.info("NativeWebViewBridge: Entering C++ blocking loop...");
                webview.run();

                if (disposed.get()) {
                    log.info("NativeWebViewBridge: Webview closed normally via dispose.");
                } else {
                    throw new RuntimeException("Webview event loop exited unexpectedly (without dispose)");
                }

            } catch (Throwable t) {
                log.error("CRITICAL: Webview crashed on thread " + Thread.currentThread().getName(), t);
                ready.set(false);
                this.nativeHandle = 0L;
                retryCount++;

                log.warn("NativeWebViewBridge: Abandoning broken Webview instance to prevent JVM crash.");
                webview = null;

                if (retryCount < MAX_RETRIES && !disposed.get()) {
                    log.info("NativeWebViewBridge: Restarting browser engine on a NEW thread in 1.5 seconds...");
                    try {
                        Thread.sleep(1500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    startWebviewThread();
                } else {
                    log.error("NativeWebViewBridge: Max retries reached. Browser engine is permanently dead.");
                }
            }
        }, "spark-webview-thread-" + System.currentTimeMillis());

        webviewThread.setDaemon(true);
        webviewThread.start();
    }

    public void hibernate() {
        if (!ready.get()) return;
        long handle = getHandleSafe();
        if (handle != 0 && SystemUtils.isWindows()) {
            NativeWindowUtils.setVisible(handle, false);
            NativeWindowUtils.setBounds(handle, -15000, -15000, 10, 10);
        } else {
            setVisible(false);
        }
    }

    public void wakeup(long parentHandle) {
        this.parentHandle = parentHandle;
        if (!ready.get()) return;
        long handle = getHandleSafe();
        if (handle != 0 && SystemUtils.isWindows()) {
            NativeWindowUtils.setParent(handle, parentHandle);
            NativeWindowUtils.setBounds(handle, nativeX, nativeY, nativeW, nativeH);
            NativeWindowUtils.setVisible(handle, true);
        } else {
            setVisible(true);
        }
    }

    private void awaitReady() {
        int waits = 0;
        while (!ready.get() && !disposed.get() && retryCount < MAX_RETRIES) {
            try {
                Thread.sleep(50);
                waits++;
                if (waits > 100) break;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void dispatch(Runnable action) {
        if (disposed.get() || retryCount >= MAX_RETRIES) return;
        awaitReady();

        var currentWebview = webview;
        if (currentWebview != null && ready.get()) {
            try {
                currentWebview.dispatch(() -> {
                    try {
                        action.run();
                    } catch (Exception e) {
                        log.error("NativeWebViewBridge: Task failed with exception", e);
                    }
                });
            } catch (Throwable t) {
                log.warn("NativeWebViewBridge: Failed to dispatch to webview", t);
            }
        }
    }

    private void bindJsFunctions() {
        webview.bind("sparkZoom", (String req) -> {
            try {
                if (!appPreferences.isZoomEnabled() || req == null || req.isBlank()) return null;

                // Входящая строка выглядит как '["up"]', '["down"]' или '["reset"]'. Очищаем ее от мусора
                String direction = req.replaceAll("[\"\\[\\]\\s]", "");

                Platform.runLater(() -> {
                    switch (direction) {
                        case "up" -> changeZoom(true);
                        case "down" -> changeZoom(false);
                        case "reset" -> resetZoom();
                    }
                });
            } catch (Exception e) {
                log.error("NativeWebViewBridge: Caught exception in sparkZoom JS callback!", e);
            }
            return null; // Аналог JsonNull
        });

        webview.bind("sparkUrl", (String req) -> {
            try {
                if (req == null || req.isBlank()) return null;

                // Входящая строка выглядит как '["https://chat.mistral.ai/"]'. Парсим через Rson
                JsonArray args = Rson.DEFAULT.fromJson(req, JsonArray.class);
                if (args == null || args.isEmpty()) return null;

                String url = args.get(0).getAsString();
                if (!url.isBlank() && !url.equals("about:blank") && onUrlChanged != null) {
                    Platform.runLater(() -> onUrlChanged.accept(url));
                }
            } catch (Exception e) {
                log.error("NativeWebViewBridge: Caught exception in sparkUrl JS callback!", e);
            }
            return null; // Аналог JsonNull
        });
    }

    public void setCurrentConfig(AiConfiguration.AiConfig config) {
        log.info("NativeWebViewBridge: Changing config to: {}", config.url());
        this.currentConfig = config;
        navigate(config.url());
    }

    public void navigate(String url) {
        this.currentUrl = url;

        long navId = System.currentTimeMillis();
        this.currentNavId = navId;

        dispatch(() -> {
            if (webview != null && currentNavId == navId) {
                log.debug("NativeWebViewBridge: Soft cease triggered (about:blank) for [Nav-{}]", navId);
                webview.loadURL("about:blank");
            }
        });

        new Thread(() -> {
            try {
                Thread.sleep(150);
            } catch (InterruptedException ignored) {
            }

            if (navId == this.currentNavId) {
                dispatch(() -> {
                    if (webview != null && currentNavId == navId) {
                        log.info("NativeWebViewBridge: Loading actual URL: {} [Nav-{}]", url, navId);
                        webview.loadURL(url);
                    } else {
                        log.debug("NativeWebViewBridge: URL Load cancelled. Newer navigation exists.");
                    }
                });

                try {
                    Thread.sleep(800);
                } catch (InterruptedException ignored) {
                }

                if (navId == this.currentNavId) {
                    dispatch(() -> {
                        if (webview != null) applyZoomCss(currentZoom);
                    });
                }
            } else {
                log.debug("NativeWebViewBridge: Navigation sequence [Nav-{}] cancelled entirely.", navId);
            }
        }, "spark-navigate-" + navId).start();
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

        long handle = getHandleSafe();
        if (handle != 0) {
            NativeWindowUtils.setBounds(handle, x, y, width, height);
        }
    }

    public void setVisible(boolean visible) {
        if (!ready.get()) return;
        long handle = getHandleSafe();
        if (handle != 0) {
            NativeWindowUtils.setVisible(handle, visible);
        }
    }

    private void changeZoom(boolean increase) {
        var step = 0.5;
        var newLevel = currentZoom + (increase ? step : -step);
        newLevel = Math.max(-3.0, Math.min(4.0, newLevel));
        setZoomInternal(newLevel);
    }

    private void setZoomInternal(double level) {
        this.currentZoom = level;
        appPreferences.setLastZoomValue(level);
        dispatch(() -> {
            if (webview != null) applyZoomCss(level);
        });
        updateZoomDisplay(level);
    }

    public void resetZoom() {
        setZoomInternal(0.0);
    }

    public void setZoomEnabled(boolean enabled) {
        appPreferences.setZoomEnabled(enabled);
        if (!enabled) {
            resetZoom();
        }
    }

    private void applyZoomCss(double level) {
        var scale = Math.pow(1.2, level);
        var js = String.format("document.documentElement.style.zoom='%.4f';", scale);
        webview.eval(js);
    }

    private void updateZoomDisplay(double level) {
        if (zoomCallback != null) {
            var pct = Math.pow(1.2, level) * 100.0;
            var displayVal = Math.round(pct / 5.0) * 5.0;
            Platform.runLater(() -> zoomCallback.accept(displayVal));
        }
    }

    public void clearCookies() {
        log.info("NativeWebViewBridge: Clearing cookies...");
        var returnUrl = currentUrl != null ? currentUrl : "about:blank";
        long navId = System.currentTimeMillis();
        this.currentNavId = navId;

        dispatch(() -> {
            if (webview != null && currentNavId == navId) {
                webview.eval("""
                        (function() {
                            document.cookie.split(';').forEach(function(c) {
                                document.cookie = c.trim().split('=')[0] +
                                    '=;expires=Thu, 01 Jan 1970 00:00:00 GMT;path=/';
                            });
                        })();
                        """);
                webview.loadURL("about:blank");
            }

            new Thread(() -> {
                try {
                    Thread.sleep(400);
                } catch (InterruptedException ignored) {
                }

                if (currentNavId == navId) {
                    dispatch(() -> {
                        if (webview != null) webview.loadURL(returnUrl);
                    });
                }
            }, "spark-cookie-clear-" + navId).start();
        });
    }

    public void shutdown(Runnable onComplete) {
        if (disposed.getAndSet(true)) return;
        log.info("NativeWebViewBridge: Shutting down...");

        if (webview != null && ready.get()) {
            dispatch(() -> {
                if (webview != null) {
                    try {
                        webview.close();
                    } catch (Throwable e) {
                        log.error("NativeWebViewBridge: Error while closing webview", e);
                    }
                }
                Platform.runLater(onComplete);
            });
        } else {
            onComplete.run();
        }
    }

    private long getHandleSafe() {
        return nativeHandle;
    }

    public boolean isReady() {
        return ready.get() && !disposed.get();
    }
}
