package to.sparkapp.app.browser;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import to.sparkapp.app.config.AiConfiguration;
import to.sparkapp.app.utils.UrlUtils;

import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
public class WebviewNavigator {

    private final WebviewManager bridge;
    private final WebviewZoomManager zoomManager;

    private volatile long currentNavId = 0L;
    private volatile String currentUrl;
    private volatile String configBaseUrl;

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                var t = new Thread(r, "spark-nav-scheduler");
                t.setDaemon(true);
                return t;
            });

    @Setter
    private Consumer<String> onUrlChanged;

    WebviewNavigator(WebviewManager bridge, WebviewZoomManager zoomManager) {
        this.bridge = bridge;
        this.zoomManager = zoomManager;
    }

    void handleUrlChange(String url) {
        if (url.isBlank() || url.equals("about:blank")) return;

        if (onUrlChanged != null) onUrlChanged.accept(url);

        if (configBaseUrl != null && !isAuthUrl(url) && !isSameHost(url, configBaseUrl)) {
            log.info("WebviewNavigator: External URL detected [{}], opening in browser", url);
            UrlUtils.openLink(url);
            final long navId = currentNavId;
            schedule(80, () -> {
                if (currentNavId == navId) {
                    bridge.dispatch(() -> bridge.loadURL(configBaseUrl));
                }
            });
        }
    }

    void setCurrentConfig(AiConfiguration.AiConfig config) {
        log.info("WebviewNavigator: Changing config to: {}", config.url());
        this.configBaseUrl = config.url();
        navigate(config.url());
    }

    void navigate(String url) {
        this.currentUrl = url;
        final long navId = System.currentTimeMillis();
        this.currentNavId = navId;

        bridge.dispatch(() -> {
            if (currentNavId == navId) {
                log.info("WebviewNavigator: Loading {} [Nav-{}]", url, navId);
                bridge.loadURL(url);
            }
        });

        schedule(500, () -> {
            if (navId == currentNavId) {
                bridge.dispatch(zoomManager::applyZoomCss);
            }
        });
    }

    void clearCookies() {
        log.info("WebviewNavigator: Clearing cookies...");
        final String returnUrl = currentUrl != null ? currentUrl : "about:blank";
        final long navId = System.currentTimeMillis();
        this.currentNavId = navId;

        bridge.dispatch(() -> {
            if (currentNavId != navId) return;
            bridge.eval("""
                    (function() {
                        document.cookie.split(';').forEach(function(c) {
                            document.cookie = c.trim().split('=')[0] +
                                '=;expires=Thu, 01 Jan 1970 00:00:00 GMT;path=/';
                        });
                    })();
                    """);
            bridge.loadURL("about:blank");
            schedule(400, () -> {
                if (currentNavId == navId) {
                    bridge.dispatch(() -> bridge.loadURL(returnUrl));
                }
            });
        });
    }

    String getCurrentUrl() {
        return currentUrl != null ? currentUrl : "about:blank";
    }

    private static boolean isSameHost(String url, String baseUrl) {
        try {
            var host1 = normalizeHost(URI.create(url).getHost());
            var host2 = normalizeHost(URI.create(baseUrl).getHost());
            if (host1 == null || host2 == null) return true;
            return host1.equals(host2) || host1.endsWith("." + host2) || host2.endsWith("." + host1);
        } catch (Exception e) {
            return true;
        }
    }

    private static String normalizeHost(String host) {
        if (host == null) return null;
        return host.startsWith("www.") ? host.substring(4) : host;
    }

    public static Boolean isAuthUrl(String url) {
        if (url == null) {
            return null;
        }

        var lower = url.toLowerCase();
        return lower.contains("accounts.google.com") || lower.contains("consent.google.com")
                || lower.contains("appleid.apple.com")
                || lower.contains("github.com/login")
                || lower.contains("github.com/session")
                || lower.contains("oauth")
                || lower.contains("/auth/")
                || lower.contains("/sso/")
                || lower.contains("signin")
                || lower.contains("login");
    }

    private void schedule(long delayMs, Runnable task) {
        scheduler.schedule(task, delayMs, TimeUnit.MILLISECONDS);
    }
}
