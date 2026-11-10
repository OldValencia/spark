package to.sparkapp.app.ui.webview;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import to.sparkapp.app.browser.WebviewManager;
import to.sparkapp.app.browser.ZoomSteps;
import to.sparkapp.app.config.AiConfiguration.AiConfig;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.utils.UrlUtils;

/**
 * The page of one site, whether it is on screen or not. Page actions do nothing until the site has been opened.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class SitePage {
    private final AiConfig site;
    /** Null while the site has not been opened. */
    private final WebviewManager browser;
    private final AppPreferences preferences;

    public boolean isOpen() {
        return browser != null;
    }

    public void goBack() {
        if (isOpen()) {
            browser.goBack();
        }
    }

    public void reload() {
        if (isOpen()) {
            browser.reload();
        }
    }

    /** The page the site shows now, or the page it will open with when it has not been opened yet. */
    public String address() {
        var current = isOpen() ? browser.getCurrentUrl() : null;

        if (UrlUtils.isWebUrl(current)) {
            return current;
        }

        var remembered = preferences.getSiteUrl(site);

        return remembered != null ? remembered : site.url();
    }

    public boolean canZoom() {
        return isOpen() && preferences.isZoomEnabled();
    }

    public double zoomPercent() {
        if (isOpen()) {
            return browser.getZoomPercent();
        }

        return preferences.isZoomEnabled() ? ZoomSteps.storedPercent(preferences.getSiteZoomLevel(site.id())) : 100;
    }

    public void zoomIn() {
        if (isOpen()) {
            browser.zoomIn();
        }
    }

    public void zoomOut() {
        if (isOpen()) {
            browser.zoomOut();
        }
    }

    public void resetZoom() {
        if (isOpen()) {
            browser.resetZoom();
        }
    }
}
