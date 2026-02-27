package to.sparkapp.app.browser;

import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import netscape.javascript.JSObject;
import to.sparkapp.app.config.AiConfiguration;
import to.sparkapp.app.config.AppPaths;
import to.sparkapp.app.config.AppPreferences;

import java.io.File;
import java.net.CookieManager;
import java.net.URI;
import java.util.function.Consumer;

@Slf4j
public class MacOsWebviewBridge {

    private static final String CHROME_USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/121.0.0.0 Safari/537.36";

    private final WebView webView;
    private final WebEngine engine;
    private final AppPreferences appPreferences;
    private final ZoomBridge zoomBridge = new ZoomBridge();

    private double currentZoom;
    private String configBaseUrl;
    private String currentUrl;

    @Setter
    private Runnable onReadyCallback;
    @Setter
    private Consumer<Double> zoomCallback;
    @Setter
    private Consumer<String> onUrlChangedCallback;
    @Setter
    private Consumer<Boolean> onAuthPageDetected;

    public MacOsWebviewBridge(AppPreferences appPreferences) {
        this.appPreferences = appPreferences;
        this.currentZoom = appPreferences.getLastZoomValue();

        webView = new WebView();
        webView.setContextMenuEnabled(false);
        engine = webView.getEngine();

        setupPersistentStorage();
        applyWebPageSettings();
        setupListeners();
    }

    private void setupPersistentStorage() {
        var webviewDataDir = new File(AppPaths.DATA_DIR, "webview-data");
        if (!webviewDataDir.exists()) {
            webviewDataDir.mkdirs();
        }
        engine.setUserDataDirectory(webviewDataDir);
        log.info("MacOsWebviewBridge: WebKit data dir: {}", webviewDataDir.getAbsolutePath());
    }

    /**
     * Sets User-Agent and optionally dark color scheme via reflection on WebPage.
     * Requires --add-opens=javafx.web/javafx.scene.web=ALL-UNNAMED (see build.gradle macOpts).
     */
    private void applyWebPageSettings() {
        try {
            var pageField = engine.getClass().getDeclaredField("page");
            pageField.setAccessible(true);
            var page = pageField.get(engine);

            try {
                var setUA = page.getClass().getMethod("setUserAgent", String.class);
                setUA.invoke(page, CHROME_USER_AGENT);
                log.info("MacOsWebviewBridge: User-Agent set");
            } catch (Exception e) {
                log.warn("MacOsWebviewBridge: Could not set User-Agent: {}", e.getMessage());
            }

            if (appPreferences.isDarkModeEnabled()) {
                try {
                    var setColorScheme = page.getClass().getMethod("setColorScheme", int.class);
                    setColorScheme.invoke(page, 1);
                    log.info("MacOsWebviewBridge: Dark color scheme set");
                } catch (Exception e) {
                    log.warn("MacOsWebviewBridge: Could not set dark color scheme: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("MacOsWebviewBridge: Could not access WebPage (missing --add-opens?): {}", e.getMessage());
        }
    }

    private void setupListeners() {
        engine.getLoadWorker().stateProperty().addListener((obs, old, state) -> {
            if (state != Worker.State.SUCCEEDED) {
                return;
            }
            registerZoomBridgeInJs();
            injectViewport();
            injectDarkModeHint();
            injectZoomHooks();
            applyZoom();
            if (onReadyCallback != null) {
                onReadyCallback.run();
            }
        });

        engine.locationProperty().addListener((obs, old, url) -> {
            if (url == null || url.isBlank() || url.equals("about:blank")) {
                return;
            }
            currentUrl = url;
            if (onUrlChangedCallback != null) {
                onUrlChangedCallback.accept(url);
            }
            var isAuth = WebviewNavigator.isAuthUrl(url);
            if (onAuthPageDetected != null) {
                onAuthPageDetected.accept(isAuth);
            }
            if (configBaseUrl != null && !isAuth && !isSameHost(url, configBaseUrl)) {
                log.info("MacOsWebviewBridge: external URL [{}], opening in browser", url);
                openInSystemBrowser(url);
                Platform.runLater(() -> engine.load(configBaseUrl));
            }
        });
    }

    private void registerZoomBridgeInJs() {
        try {
            var jsWindow = (JSObject) engine.executeScript("window");
            jsWindow.setMember("__sparkZoom", zoomBridge);
        } catch (Exception e) {
            log.warn("MacOsWebviewBridge: Could not register zoom bridge in JS", e);
        }
    }

    /**
     * JavaFX WebView does not set a viewport meta tag by default.
     * Without it, many sites render at a fixed ~980px desktop width and ignore
     * the actual window size. This injects the tag if the page doesn't have one.
     */
    private void injectViewport() {
        var js = """
                (function() {
                    var existing = document.querySelector('meta[name="viewport"]');
                    if (!existing) {
                        var meta = document.createElement('meta');
                        meta.name = 'viewport';
                        meta.content = 'width=device-width, initial-scale=1.0';
                        document.head.appendChild(meta);
                    }
                })();
                """;
        try {
            engine.executeScript(js);
        } catch (Exception ignored) {
        }
    }

    /**
     * Injects a <meta name="color-scheme"> tag as a fallback for sites that
     * check it in addition to the CSS prefers-color-scheme media feature.
     * Only runs when dark mode is enabled.
     */
    private void injectDarkModeHint() {
        if (!appPreferences.isDarkModeEnabled()) {
            return;
        }
        var js = """
                (function() {
                    var existing = document.querySelector('meta[name="color-scheme"]');
                    if (!existing) {
                        var meta = document.createElement('meta');
                        meta.name = 'color-scheme';
                        meta.content = 'dark';
                        document.head.appendChild(meta);
                    }
                })();
                """;
        try {
            engine.executeScript(js);
        } catch (Exception ignored) {
        }
    }

    private void injectZoomHooks() {
        var js = """
                (function() {
                    if (window.__sparkZoomHooked) return;
                    window.__sparkZoomHooked = true;
                    document.addEventListener('wheel', function(e) {
                        if (!e.ctrlKey) return;
                        e.preventDefault();
                        window.__sparkZoom.zoom(e.deltaY > 0 ? 'down' : 'up');
                    }, {passive: false});
                    document.addEventListener('keydown', function(e) {
                        if (!e.ctrlKey) return;
                        if (e.key === '=' || e.key === '+') { e.preventDefault(); window.__sparkZoom.zoom('up'); }
                        if (e.key === '-') { e.preventDefault(); window.__sparkZoom.zoom('down'); }
                        if (e.key === '0') { e.preventDefault(); window.__sparkZoom.zoom('reset'); }
                    });
                })();
                """;
        try {
            engine.executeScript(js);
        } catch (Exception ignored) {
        }
    }

    public void init(String url) {
        currentUrl = url;
        configBaseUrl = url;
        Platform.runLater(() -> engine.load(url != null ? url : "about:blank"));
    }

    public void setCurrentConfig(AiConfiguration.AiConfig config) {
        configBaseUrl = config.url();
        currentUrl = config.url();
        Platform.runLater(() -> engine.load(config.url()));
    }

    public void navigate(String url) {
        currentUrl = url;
        Platform.runLater(() -> engine.load(url));
    }

    public void clearCookies() {
        Platform.runLater(() -> {
            engine.load("about:blank");
            try {
                var cm = (CookieManager) java.net.CookieHandler.getDefault();
                if (cm != null) {
                    cm.getCookieStore().removeAll();
                }
            } catch (Exception ignored) {
            }
            Platform.runLater(() -> engine.load(currentUrl != null ? currentUrl : "about:blank"));
        });
    }

    public void handleZoom(String direction) {
        if (!appPreferences.isZoomEnabled()) {
            return;
        }
        switch (direction) {
            case "up" -> changeZoom(true);
            case "down" -> changeZoom(false);
            case "reset" -> resetZoom();
        }
    }

    private void changeZoom(boolean increase) {
        var step = 0.5;
        var newLevel = currentZoom + (increase ? step : -step);
        newLevel = Math.max(-3.0, Math.min(4.0, newLevel));
        setZoomLevel(newLevel);
    }

    public void resetZoom() {
        setZoomLevel(0.0);
    }

    private void setZoomLevel(double level) {
        currentZoom = level;
        appPreferences.setLastZoomValue(level);
        applyZoom();
        if (zoomCallback != null) {
            var pct = Math.pow(1.2, level) * 100.0;
            var displayVal = Math.round(pct / 5.0) * 5.0;
            zoomCallback.accept(displayVal);
        }
    }

    private void applyZoom() {
        var scale = Math.pow(1.2, currentZoom);
        try {
            engine.executeScript(String.format("document.documentElement.style.zoom='%.4f';", scale));
        } catch (Exception ignored) {
        }
    }

    public WebView getWebView() {
        return webView;
    }

    public void shutdown(Runnable onComplete) {
        Platform.runLater(() -> {
            engine.load("about:blank");
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    public String getCurrentUrl() {
        return currentUrl;
    }

    private static boolean isSameHost(String url, String baseUrl) {
        try {
            var h1 = normalizeHost(URI.create(url).getHost());
            var h2 = normalizeHost(URI.create(baseUrl).getHost());
            if (h1 == null || h2 == null) {
                return true;
            }
            return h1.equals(h2) || h1.endsWith("." + h2) || h2.endsWith("." + h1);
        } catch (Exception e) {
            return true;
        }
    }

    private static String normalizeHost(String host) {
        if (host == null) {
            return null;
        }
        return host.startsWith("www.") ? host.substring(4) : host;
    }

    private static void openInSystemBrowser(String url) {
        try {
            java.awt.Desktop.getDesktop().browse(URI.create(url));
        } catch (Exception e) {
            log.error("Cannot open URL in browser", e);
        }
    }

    public class ZoomBridge {
        public void zoom(String dir) {
            Platform.runLater(() -> handleZoom(dir));
        }
    }
}
