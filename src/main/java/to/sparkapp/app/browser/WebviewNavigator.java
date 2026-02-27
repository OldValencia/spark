package to.sparkapp.app.browser;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import to.sparkapp.app.config.AiConfiguration;
import to.sparkapp.app.utils.UrlUtils;

import java.net.URI;
import java.util.List;
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

    private static final List<String> AUTH_DOMAINS = List.of(
            "accounts.google.",
            "consent.google.",
            "auth.openai.",
            "appleid.apple.com",
            "idmsa.apple.com",
            "login.microsoftonline.com",
            "login.live.com",
            "login.windows.net",
            "account.microsoft.com",
            "login.facebook.com",
            "www.facebook.com/login",
            "github.com/login",
            "github.com/session",
            "github.com/oauth",
            "challenges.cloudflare.com",
            "cloudflare.com/cdn-cgi/challenge"
    );

    private static final List<String> AUTH_PATH_PATTERNS = List.of(
            "/oauth",
            "/oauth2",
            "/auth/",
            "/authorize",
            "/sso/",
            "/saml/",
            "/signin",
            "/login",
            "/callback?code=",
            "/oidc/"
    );

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

    public static boolean isAuthUrl(String url) {
        if (url == null) {
            return false;
        }
        var lower = url.toLowerCase();

        for (var domain : AUTH_DOMAINS) {
            if (lower.contains(domain)) {
                return true;
            }
        }
        for (var pattern : AUTH_PATH_PATTERNS) {
            if (lower.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    private void schedule(long delayMs, Runnable task) {
        scheduler.schedule(task, delayMs, TimeUnit.MILLISECONDS);
    }
}
