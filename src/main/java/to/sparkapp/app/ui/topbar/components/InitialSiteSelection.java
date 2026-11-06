package to.sparkapp.app.ui.topbar.components;

import java.util.List;

import to.sparkapp.app.config.AiConfiguration.AiConfig;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.utils.UrlUtils;

record InitialSiteSelection(int index, String url) {
    static InitialSiteSelection resolve(List<AiConfig> orderedConfigs, AppPreferences preferences, String activeId) {
        var lastUrl = preferences.getLastUrl();
        int initialIndex = 0;
        String initialUrl = null;

        // The remembered URL is usually a page inside a provider (a conversation),
        // so the provider is matched by host rather than by an exact URL match.
        if (lastUrl != null) {
            for (int i = 0; i < orderedConfigs.size(); i++) {
                var configUrl = orderedConfigs.get(i).url();

                if (configUrl.equals(lastUrl) || UrlUtils.matchingBaseUrl(lastUrl, List.of(configUrl)) != null) {
                    initialIndex = i;
                    initialUrl = lastUrl;
                    break;
                }
            }
        }

        if (preferences.isRememberLastAi() && preferences.getLastSiteId() != null) {
            for (int i = 0; i < orderedConfigs.size(); i++) {
                if (orderedConfigs.get(i).id().equals(preferences.getLastSiteId())) {
                    initialIndex = i;
                    initialUrl = preferences.getSiteUrl(orderedConfigs.get(i));
                    break;
                }
            }
        }

        if (activeId != null) {
            for (int i = 0; i < orderedConfigs.size(); i++) {
                if (orderedConfigs.get(i).id().equals(activeId)) {
                    initialIndex = i;
                    initialUrl = null;
                    break;
                }
            }
        }

        return new InitialSiteSelection(initialIndex, initialUrl);
    }
}
