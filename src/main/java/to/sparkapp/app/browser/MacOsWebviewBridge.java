package to.sparkapp.app.browser;

import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import to.sparkapp.app.config.AiConfiguration;
import to.sparkapp.app.config.AppPreferences;

import java.net.CookieManager;
import java.net.URI;
import java.util.function.Consumer;

@Slf4j
public class MacOsWebviewBridge {

    private final WebView webView;
    private final WebEngine engine;
    private final AppPreferences appPreferences;

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

    private static final java.util.Set<String> AUTH_KEYWORDS = java.util.Set.of(
            "accounts.google.com", "consent.google.com", "appleid.apple.com", "github.com/login",
            "github.com/session", "oauth", "/auth/", "/sso/", "signin", "login"
    );

    public MacOsWebviewBridge(AppPreferences appPreferences) {
        this.appPreferences = appPreferences;
        this.currentZoom = appPreferences.getLastZoomValue();

        webView = new WebView();
        webView.setContextMenuEnabled(false);
        engine = webView.getEngine();

        if (appPreferences.isDarkModeEnabled()) {
            try {
                var field = engine.getClass().getDeclaredField("page");
                field.setAccessible(true);
                var page = field.get(engine);
                var setColorScheme = page.getClass().getMethod("setColorScheme", int.class);
                setColorScheme.invoke(page, 1);
            } catch (Exception ignored) {
            }
        }

        setupListeners();
    }

    private void setupListeners() {
        engine.getLoadWorker().stateProperty().addListener((obs, old, state) -> {
            if (state == Worker.State.SUCCEEDED) {
                applyZoom();
                injectZoomHooks();
                if (onReadyCallback != null) onReadyCallback.run();
            }
        });

        engine.locationProperty().addListener((obs, old, url) -> {
            if (url == null || url.isBlank() || url.equals("about:blank")) return;
            currentUrl = url;
            if (onUrlChangedCallback != null) onUrlChangedCallback.accept(url);

            boolean isAuth = isAuthUrl(url);
            if (onAuthPageDetected != null) {
                onAuthPageDetected.accept(isAuth);
            }

            if (configBaseUrl != null && !isAuth && !isSameHost(url, configBaseUrl)) {
                log.info("MacOsWebViewBridge: external URL [{}], opening in browser", url);
                openInSystemBrowser(url);
                Platform.runLater(() -> engine.load(configBaseUrl));
            }
        });
    }

    private void injectZoomHooks() {
        var js = """
                (function() {
                    if (window.__sparkZoomHooked) return;
                    window.__sparkZoomHooked = true;
                    document.addEventListener('wheel', function(e) {
                        if (!e.ctrlKey) return;
                        e.preventDefault();
                        window.__sparkZoom(e.deltaY > 0 ? 'down' : 'up');
                    }, {passive: false});
                    document.addEventListener('keydown', function(e) {
                        if (!e.ctrlKey) return;
                        if (e.key === '=' || e.key === '+') { e.preventDefault(); window.__sparkZoom('up'); }
                        if (e.key === '-')                  { e.preventDefault(); window.__sparkZoom('down'); }
                        if (e.key === '0')                  { e.preventDefault(); window.__sparkZoom('reset'); }
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
                if (cm != null) cm.getCookieStore().removeAll();
            } catch (Exception ignored) {
            }
            Platform.runLater(() -> engine.load(currentUrl != null ? currentUrl : "about:blank"));
        });
    }

    public void handleZoom(String direction) {
        if (!appPreferences.isZoomEnabled()) return;
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
            if (onComplete != null) onComplete.run();
        });
    }

    public String getCurrentUrl() {
        return currentUrl;
    }

    public void registerZoomBridge() {
        try {
            var bridge = new Object() {
                public void zoom(String dir) {
                    Platform.runLater(() -> handleZoom(dir));
                }
            };
            engine.getLoadWorker().stateProperty().addListener((obs, old, state) -> {
                if (state == Worker.State.SUCCEEDED) {
                    var jsWindow = (netscape.javascript.JSObject) engine.executeScript("window");
                    jsWindow.setMember("__sparkZoom", bridge);
                }
            });
        } catch (Exception e) {
            log.warn("Could not set zoom bridge", e);
        }
    }

    private static boolean isSameHost(String url, String baseUrl) {
        try {
            var h1 = normalize(URI.create(url).getHost());
            var h2 = normalize(URI.create(baseUrl).getHost());
            if (h1 == null || h2 == null) return true;
            return h1.equals(h2) || h1.endsWith("." + h2) || h2.endsWith("." + h1);
        } catch (Exception e) {
            return true;
        }
    }

    private static String normalize(String host) {
        if (host == null) return null;
        return host.startsWith("www.") ? host.substring(4) : host;
    }

    private static boolean isAuthUrl(String url) {
        var lower = url.toLowerCase();
        return AUTH_KEYWORDS.stream().anyMatch(lower::contains);
    }

    private static void openInSystemBrowser(String url) {
        try {
            java.awt.Desktop.getDesktop().browse(URI.create(url));
        } catch (Exception e) {
            log.error("Cannot open URL in browser", e);
        }
    }
}
