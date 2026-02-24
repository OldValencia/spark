package to.sparkapp.app.browser;

import co.casterlabs.rakurai.json.element.JsonArray;
import co.casterlabs.rakurai.json.element.JsonNull;
import dev.webview.Webview;
import to.sparkapp.app.config.AiConfiguration;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.utils.NativeWindowUtils;
import to.sparkapp.app.utils.SystemUtils;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Manages a native OS WebView (WKWebView on macOS, WebView2/Edge on Windows)
 * via webview_java running on its own dedicated thread.
 *
 * <p>The webview opens as an independent OS window. CefWebView (a JPanel
 * placeholder in the Swing hierarchy) calls {@link #updateBounds} whenever it is
 * moved or resized so the native window tracks the Swing layout pixel-for-pixel.
 *
 * <p><b>Thread model:</b>
 * <ul>
 *   <li>All webview API calls MUST happen on the webview thread (via {@code dispatch()}).
 *   <li>Swing callbacks are dispatched back to the EDT via {@code SwingUtilities.invokeLater}.
 * </ul>
 */
@Slf4j
public class NativeWebViewBridge {

    private volatile Webview webview;

    private final AtomicBoolean ready = new AtomicBoolean(false);
    private final AtomicBoolean disposed = new AtomicBoolean(false);
    private final CountDownLatch readyLatch = new CountDownLatch(1);

    private volatile String currentUrl;
    private volatile double currentZoom;
    private volatile AiConfiguration.AiConfig currentConfig;

    private volatile int nativeX, nativeY, nativeW, nativeH;

    private final AppPreferences appPreferences;

    @Setter
    private Consumer<Double> zoomCallback;
    @Setter
    private Consumer<String> onUrlChanged;

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

    /**
     * @param parentHandle native HWND of the Swing JFrame (Windows) or 0 (macOS).
     *                     On Windows the webview is embedded as a child window so
     *                     z-order and clipping are handled by the OS.
     *                     x/y must then be relative to the JFrame client area.
     */
    public void init(String startUrl, long parentHandle, int x, int y, int width, int height) {
        if (disposed.get()) {
            return;
        }

        this.currentUrl = startUrl;
        this.nativeX = x;
        this.nativeY = y;
        this.nativeW = width;
        this.nativeH = height;

        NativeWindowUtils.cachedWebviewHeight = height;

        var webviewThread = new Thread(() -> {
            try {
                log.info("Initialising native webview ({}x{}) at ({},{})", width, height, x, y);

                webview = new Webview(false);

                bindJsFunctions();
                webview.setInitScript(INIT_SCRIPTS);
                webview.setSize(width, height);
                webview.loadURL(startUrl);
                applyZoomCss(currentZoom);

                ready.set(true);
                readyLatch.countDown();

                webview.dispatch(() -> {
                    long handle = webview.getNativeWindowPointer();
                    if (handle != 0) {
                        NativeWindowUtils.setParent(handle, parentHandle);
                        NativeWindowUtils.setBounds(handle, x, y, width, height);
                        log.debug("Native window handle=0x{}, positioned at ({},{}) {}x{}",
                                Long.toHexString(handle), x, y, width, height);
                    }
                });

                log.info("Native webview event loop starting");
                webview.run();
                log.info("Native webview event loop exited");

            } catch (Exception e) {
                log.error("Fatal webview error", e);
                readyLatch.countDown();
            }
        }, "spark-webview-thread");

        webviewThread.setDaemon(true);
        webviewThread.start();
    }

    public void hibernate() {
        if (!ready.get()) return;
        long handle = getHandleSafe();
        if (handle != 0 && SystemUtils.isWindows()) {
            NativeWindowUtils.setVisible(handle, false);
            NativeWindowUtils.unparent(handle);
        } else {
            setVisible(false);
        }
    }

    public void wakeup(long parentHandle) {
        if (!ready.get()) return;
        long handle = getHandleSafe();
        if (handle != 0 && SystemUtils.isWindows()) {
            NativeWindowUtils.setParent(handle, parentHandle);
            NativeWindowUtils.setVisible(handle, true);
        } else {
            setVisible(true);
        }
    }

    private void awaitReady() {
        if (ready.get()) {
            return;
        }
        try {
            readyLatch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void dispatch(Runnable r) {
        if (disposed.get() || webview == null) {
            return;
        }
        awaitReady();
        if (webview != null) {
            webview.dispatch(r);
        }
    }

    private void bindJsFunctions() {
        webview.bind("sparkZoom", (JsonArray args) -> {
            if (!appPreferences.isZoomEnabled()) {
                return JsonNull.INSTANCE;
            }
            var direction = args.get(0).getAsString();
            SwingUtilities.invokeLater(() -> {
                switch (direction) {
                    case "up" -> changeZoom(true);
                    case "down" -> changeZoom(false);
                    case "reset" -> resetZoom();
                }
            });
            return JsonNull.INSTANCE;
        });

        webview.bind("sparkUrl", (JsonArray args) -> {
            var url = args.get(0).getAsString();
            if (!url.isBlank() && !url.equals("about:blank") && onUrlChanged != null) {
                SwingUtilities.invokeLater(() -> onUrlChanged.accept(url));
            }
            return JsonNull.INSTANCE;
        });
    }

    public void setCurrentConfig(AiConfiguration.AiConfig config) {
        this.currentConfig = config;
        navigate(config.url());
    }

    public void navigate(String url) {
        this.currentUrl = url;
        dispatch(() -> {
            webview.loadURL(url);
            try {
                Thread.sleep(800);
            } catch (InterruptedException ignored) {
            }
            applyZoomCss(currentZoom);
        });
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

        dispatch(() -> webview.setSize(width, height));

        long handle = getHandleSafe();
        if (handle != 0) {
            NativeWindowUtils.setBounds(handle, x, y, width, height);
        }
    }

    public void setVisible(boolean visible) {
        if (!ready.get()) {
            return;
        }
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
        dispatch(() -> applyZoomCss(level));
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
            SwingUtilities.invokeLater(() -> zoomCallback.accept(displayVal));
        }
    }

    public void clearCookies() {
        var returnUrl = currentUrl != null ? currentUrl : "about:blank";
        dispatch(() -> {
            webview.eval("""
                    (function() {
                        document.cookie.split(';').forEach(function(c) {
                            document.cookie = c.trim().split('=')[0] +
                                '=;expires=Thu, 01 Jan 1970 00:00:00 GMT;path=/';
                        });
                    })();
                    """);
            webview.loadURL("about:blank");

            new Thread(() -> {
                try {
                    Thread.sleep(400);
                } catch (InterruptedException ignored) {
                }
                dispatch(() -> webview.loadURL(returnUrl));
            }, "spark-cookie-clear").start();
        });
    }

    public void shutdown(Runnable onComplete) {
        if (disposed.getAndSet(true)) {
            return;
        }

        if (webview != null && ready.get()) {
            webview.dispatch(() -> {
                webview.close();
                SwingUtilities.invokeLater(onComplete);
            });
        } else {
            onComplete.run();
        }
    }

    private long getHandleSafe() {
        try {
            return webview != null ? webview.getNativeWindowPointer() : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    public boolean isReady() {
        return ready.get() && !disposed.get();
    }
}
