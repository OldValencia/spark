package to.sparkapp.app.ui.webview;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javafx.application.Platform;
import lombok.Getter;
import to.sparkapp.app.browser.WebviewManager;
import to.sparkapp.app.config.AiConfiguration.AiConfig;
import to.sparkapp.app.config.AppPreferences;

final class BrowserSessions {
    private final AppPreferences appPreferences;
    private final Map<String, WebviewManager> sessions = new HashMap<>();
    private final Map<WebviewManager, AiConfig> sites = new HashMap<>();
    private final Set<WebviewManager> started = new HashSet<>();
    private boolean initialBrowserUnused = true;
    @Getter
    private WebviewManager active;

    BrowserSessions(AppPreferences appPreferences) {
        this.appPreferences = appPreferences;
        active = new WebviewManager(appPreferences);
    }

    boolean select(AiConfig config, String targetUrl, Consumer<WebviewManager> onCreated) {
        var existing = sessions.get(config.id());
        var previousConfig = existing == null ? null : sites.get(existing);

        if (previousConfig != null && !previousConfig.url().equals(config.url())) {
            existing.shutdown(() -> {
            });
            started.remove(existing);
            sites.remove(existing);
            sessions.remove(config.id());
            existing = null;
        }

        if (existing != null) {
            sites.put(existing, config);
        }

        if (existing == active) {
            return false;
        }

        active.hibernate();

        if (existing == null) {
            if (!initialBrowserUnused) {
                active = new WebviewManager(appPreferences);
            }

            initialBrowserUnused = false;
            sessions.put(config.id(), active);
            sites.put(active, config);
            onCreated.accept(active);

            var remembered = targetUrl != null ? targetUrl : appPreferences.getSiteUrl(config);
            active.setCurrentConfig(config, remembered);
        } else {
            active = existing;
        }

        return true;
    }

    public void reconcileSites(List<AiConfig> configurations) {
        sessions.entrySet().removeIf(entry -> {
            var site = sites.get(entry.getValue());
            boolean removed = configurations.stream()
                    .noneMatch(config -> config.id().equals(entry.getKey()) && config.url().equals(site.url()));

            if (removed) {
                var browser = entry.getValue();
                browser.hibernate();
                browser.shutdown(() -> {
                });
                sites.remove(browser);
                started.remove(browser);
            }

            return removed;
        });
    }

    /** The browser of a site that has been opened, or {@code null}. */
    WebviewManager find(String siteId) {
        return sessions.get(siteId);
    }

    AiConfig findSite(WebviewManager browser) {
        return sites.get(browser);
    }

    boolean isStarted(WebviewManager browser) {
        return started.contains(browser);
    }

    void markStarted(WebviewManager browser) {
        started.add(browser);
    }

    void forEach(Consumer<WebviewManager> action) {
        sessions.values().forEach(action);
    }

    void forEachSite(BiConsumer<String, WebviewManager> action) {
        sessions.forEach(action);
    }

    void shutdown(Runnable onComplete) {
        var browsers = new HashSet<>(sessions.values());
        browsers.add(active);

        var remaining = new AtomicInteger(browsers.size());
        browsers.forEach(browser -> browser.shutdown(() -> {
            if (remaining.decrementAndGet() == 0) {
                Platform.runLater(onComplete);
            }
        }));
    }
}