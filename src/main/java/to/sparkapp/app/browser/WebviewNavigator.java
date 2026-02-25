package to.sparkapp.app.browser;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import to.sparkapp.app.config.AiConfiguration;

import java.util.function.Consumer;

@Slf4j
class WebviewNavigator {

    private final WebviewManager bridge;
    private final WebviewZoomManager zoomManager;

    private volatile long currentNavId = 0L;
    private volatile String currentUrl;

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

    void navigate(String url) {
        this.currentUrl = url;
        long navId = System.currentTimeMillis();
        this.currentNavId = navId;

        bridge.dispatch(() -> {
            if (currentNavId == navId) {
                log.debug("WebviewNavigator: Soft cease triggered (about:blank) for [Nav-{}]", navId);
                bridge.loadURL("about:blank");
            }
        });

        new Thread(() -> {
            try {
                Thread.sleep(150);
            } catch (InterruptedException ignored) {
            }

            if (navId == this.currentNavId) {
                bridge.dispatch(() -> {
                    if (currentNavId == navId) {
                        log.info("WebviewNavigator: Loading actual URL: {} [Nav-{}]", url, navId);
                        bridge.loadURL(url);
                    }
                });

                try {
                    Thread.sleep(800);
                } catch (InterruptedException ignored) {
                }

                if (navId == this.currentNavId) {
                    bridge.dispatch(zoomManager::applyZoomCss);
                }
            }
        }, "spark-navigate-" + navId).start();
    }

    void clearCookies() {
        log.info("WebviewNavigator: Clearing cookies...");
        var returnUrl = currentUrl != null ? currentUrl : "about:blank";
        var navId = System.currentTimeMillis();
        this.currentNavId = navId;

        bridge.dispatch(() -> {
            if (currentNavId == navId) {
                bridge.eval("""
                        (function() {
                            document.cookie.split(';').forEach(function(c) {
                                document.cookie = c.trim().split('=')[0] +
                                    '=;expires=Thu, 01 Jan 1970 00:00:00 GMT;path=/';
                            });
                        })();
                        """);
                bridge.loadURL("about:blank");
            }

            new Thread(() -> {
                try {
                    Thread.sleep(400);
                } catch (InterruptedException ignored) {
                }
                if (currentNavId == navId) {
                    bridge.dispatch(() -> bridge.loadURL(returnUrl));
                }
            }, "spark-cookie-clear-" + navId).start();
        });
    }

    String getCurrentUrl() {
        return currentUrl != null ? currentUrl : "about:blank";
    }
}
