package to.sparkapp.app.ui.webview;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import to.sparkapp.app.browser.WebviewManager;
import to.sparkapp.app.config.AiConfiguration.AiConfig;
import to.sparkapp.app.config.AppPreferences;

/** Uses the isolated profile of the native test run. */
@Tag("native")
class SitePageTest {
    private static final AiConfig SITE = new AiConfig("site-page-test", "Chat", "https://chat.example.com/", "#123456",
            null);
    private final AppPreferences preferences = new AppPreferences();
    private boolean zoomWasEnabled;

    @BeforeEach
    void enableZoom() {
        zoomWasEnabled = preferences.isZoomEnabled();
        preferences.setZoomEnabled(true);
    }

    @AfterEach
    void restoreZoom() {
        preferences.setSiteZoomLevel(SITE.id(), 0.0);
        preferences.setZoomEnabled(zoomWasEnabled);
        preferences.flush();
    }

    @Test
    void aSiteThatWasNeverOpenedShowsItsStoredPageAndZoom() {
        preferences.setSiteZoomLevel(SITE.id(), 1.0);
        preferences.rememberSitePage(SITE, "https://chat.example.com/c/42", false);

        var page = new SitePage(SITE, null, preferences);

        assertFalse(page.isOpen());
        assertFalse(page.canZoom());
        assertEquals(120, page.zoomPercent());
        assertEquals("https://chat.example.com/c/42", page.address());

        page.zoomIn();
        page.goBack();

        assertEquals(120, page.zoomPercent(), "Actions wait until the site is opened");
    }

    @Test
    void zoomChangesTheSiteItBelongsTo() {
        var browser = new WebviewManager(preferences);
        browser.setCurrentConfig(SITE, SITE.url());

        var page = new SitePage(SITE, browser, preferences);
        page.zoomIn();

        assertTrue(page.canZoom());
        assertEquals(110, page.zoomPercent());
        assertEquals(0.5, preferences.getSiteZoomLevel(SITE.id()));

        page.resetZoom();

        assertEquals(100, page.zoomPercent());
        browser.shutdown(() -> {
        });
    }
}
