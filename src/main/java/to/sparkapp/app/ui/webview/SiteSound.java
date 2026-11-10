package to.sparkapp.app.ui.webview;

import lombok.RequiredArgsConstructor;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.config.MuteRule;

/** Mutes each open site by its own choice and by whether Spark is hidden. */
@RequiredArgsConstructor
final class SiteSound {
    private final BrowserSessions sessions;
    private final AppPreferences preferences;
    private boolean appHidden;

    void setAppHidden(boolean hidden) {
        if (appHidden != hidden) {
            appHidden = hidden;
            refresh();
        }
    }

    void refresh() {
        sessions.forEachSite((siteId, browser) -> browser.setMuted(isMuted(siteId)));
    }

    private boolean isMuted(String siteId) {
        if (preferences.isSiteMuted(siteId, MuteRule.ALWAYS)) {
            return true;
        }

        return appHidden && preferences.isSiteMuted(siteId, MuteRule.WHILE_HIDDEN);
    }
}
