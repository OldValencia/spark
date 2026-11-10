package to.sparkapp.app.config;

import java.util.HashMap;
import java.util.List;

import lombok.RequiredArgsConstructor;
import to.sparkapp.app.config.AiConfiguration.AiConfig;

/**
 * Settings stored for each site by its id.
 *
 * <p>Accessed under the owning AppPreferences monitor with its shared configuration.
 */
@RequiredArgsConstructor
final class SiteSettingsStore {
    private final AppConfig config;
    private final Runnable changed;

    double getZoomLevel(String siteId) {
        var level = find(siteId).zoomLevel();

        return level != null ? level : 0.0;
    }

    void setZoomLevel(String siteId, double level) {
        var stored = level == 0.0 ? null : level;
        put(siteId, find(siteId).withZoomLevel(stored));
        changed.run();
    }

    void clearZoomLevels() {
        if (config.siteSettings == null) {
            return;
        }

        config.siteSettings.replaceAll((siteId, settings) -> settings.withZoomLevel(null));
        removeEmptySettings();
    }

    /** The site's own value, or {@code null} when it follows the default for all sites. */
    Boolean getMute(String siteId, MuteRule rule) {
        return find(siteId).mute(rule);
    }

    void setMute(String siteId, MuteRule rule, Boolean muted) {
        put(siteId, find(siteId).withMute(rule, muted));
        changed.run();
    }

    /** Makes every site follow the default for all sites again. */
    void clearMute(MuteRule rule) {
        if (config.siteSettings == null) {
            return;
        }

        config.siteSettings.replaceAll((siteId, settings) -> settings.withMute(rule, null));
        removeEmptySettings();
    }

    void prune(List<AiConfig> sites) {
        if (config.siteSettings == null) {
            return;
        }

        config.siteSettings.keySet().removeIf(siteId -> sites.stream().noneMatch(site -> site.id().equals(siteId)));
        removeEmptySettings();
        changed.run();
    }

    private SiteSettings find(String siteId) {
        var settings = config.siteSettings == null ? null : config.siteSettings.get(siteId);

        return settings != null ? settings : SiteSettings.DEFAULTS;
    }

    private void put(String siteId, SiteSettings settings) {
        if (config.siteSettings == null) {
            config.siteSettings = new HashMap<>();
        }

        config.siteSettings.put(siteId, settings);
        removeEmptySettings();
    }

    /** Keeps the file free of sites that only use defaults. */
    private void removeEmptySettings() {
        config.siteSettings.values().removeIf(settings -> settings == null || settings.isEmpty());

        if (config.siteSettings.isEmpty()) {
            config.siteSettings = null;
        }
    }
}
