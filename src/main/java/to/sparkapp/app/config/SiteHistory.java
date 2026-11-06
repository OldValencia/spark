package to.sparkapp.app.config;

import java.util.HashMap;
import java.util.List;

import lombok.RequiredArgsConstructor;
import to.sparkapp.app.config.AiConfiguration.AiConfig;
import to.sparkapp.app.utils.UrlUtils;

/**
 * Accessed under the owning AppPreferences monitor with its shared configuration.
 */
@RequiredArgsConstructor
final class SiteHistory {
    private final AppConfig config;
    private final Runnable save;
    private final Runnable changed;

    String getSiteUrl(AiConfig site) {
        if (!Boolean.TRUE.equals(config.rememberLastAi) || config.siteUrls == null) {
            return null;
        }

        var url = config.siteUrls.get(site.id());

        return UrlUtils.isSameHost(url, site.url()) ? url : null;
    }

    void rememberSitePage(AiConfig site, String url, boolean active) {
        if (!Boolean.TRUE.equals(config.rememberLastAi) || !UrlUtils.isSameHost(url, site.url())) {
            return;
        }

        if (config.siteUrls == null) {
            config.siteUrls = new HashMap<>();
        }

        config.siteUrls.put(site.id(), url);

        if (active) {
            config.lastSiteId = site.id();
            config.lastUrl = url;
        }

        changed.run();
    }

    void clearSiteHistory() {
        config.siteUrls = new HashMap<>();
        config.lastSiteId = null;
        config.lastUrl = null;
        save.run();
    }

    void pruneSiteHistory(List<AiConfig> sites) {
        if (config.siteUrls == null) {
            return;
        }

        config.siteUrls.entrySet().removeIf(entry -> sites.stream().noneMatch(
                site -> site.id().equals(entry.getKey()) && UrlUtils.isSameHost(entry.getValue(), site.url())));

        if (sites.stream().noneMatch(site -> site.id().equals(config.lastSiteId))) {
            config.lastSiteId = null;
        }

        changed.run();
    }

}
