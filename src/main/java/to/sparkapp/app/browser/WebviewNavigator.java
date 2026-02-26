package to.sparkapp.app.browser;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import to.sparkapp.app.config.AiConfiguration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
class WebviewNavigator {

    private final WebviewManager bridge;
    private final WebviewZoomManager zoomManager;

    private volatile long currentNavId = 0L;
    private volatile String currentUrl;

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
        if (!url.isBlank() && !url.equals("about:blank") && onUrlChanged != null) {
            onUrlChanged.accept(url);
        }
    }

    void setCurrentConfig(AiConfiguration.AiConfig config) {
        log.info("WebviewNavigator: Changing config to: {}", config.url());
        navigate(config.url());
    }

    /**
     * Navigates directly to the target URL.
     */
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

        // Apply zoom after the page has had time to load.
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

    private void schedule(long delayMs, Runnable task) {
        scheduler.schedule(task, delayMs, TimeUnit.MILLISECONDS);
    }
}
