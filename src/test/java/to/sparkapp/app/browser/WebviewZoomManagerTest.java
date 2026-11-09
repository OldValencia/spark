package to.sparkapp.app.browser;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.config.TestPreferences;

class WebviewZoomManagerTest {
    @TempDir
    Path directory;

    private AppPreferences preferences;
    private final List<Double> rendered = new ArrayList<>();

    @BeforeEach
    void createPreferences() {
        preferences = TestPreferences.at(directory.resolve("settings.json").toFile());
    }

    @Test
    void zoomChangesBelongToTheSiteShownByThatBrowser() {
        var chat = new WebviewZoomManager(preferences, rendered::add);
        var notes = new WebviewZoomManager(preferences, rendered::add);
        chat.showSite("chat");
        notes.showSite("notes");

        chat.handleZoomCommand("up");
        chat.handleZoomCommand("up");

        assertEquals(120.0, chat.getZoomPercent());
        assertEquals(100.0, notes.getZoomPercent());
        assertEquals(1.0, preferences.getSiteZoomLevel("chat"));
        assertEquals(0.0, preferences.getSiteZoomLevel("notes"));
        assertEquals(1.2, rendered.getLast(), 1e-9);
    }

    @Test
    void browserOpenedLaterRestoresTheStoredZoom() {
        preferences.setSiteZoomLevel("chat", -1.0);
        var browser = new WebviewZoomManager(preferences, rendered::add);

        browser.showSite("chat");

        assertEquals(83.0, browser.getZoomPercent());
        assertEquals(1 / 1.2, rendered.getLast(), 1e-9);
    }

    @Test
    void storedValuesOutsideTheScaleAreLimited() {
        preferences.setSiteZoomLevel("chat", 40.0);
        var browser = new WebviewZoomManager(preferences, rendered::add);

        browser.showSite("chat");
        browser.handleZoomCommand("up");

        assertEquals(207.0, browser.getZoomPercent());
        assertEquals(4.0, preferences.getSiteZoomLevel("chat"));
    }

    @Test
    void switchedOffZoomRendersEverySiteAtFullSize() {
        var browser = new WebviewZoomManager(preferences, rendered::add);
        browser.showSite("chat");
        browser.handleZoomCommand("up");

        preferences.setZoomEnabled(false);
        browser.refresh();
        browser.handleZoomCommand("up");

        assertEquals(100.0, browser.getZoomPercent());
        assertEquals(1.0, rendered.getLast());

        preferences.setZoomEnabled(true);
        browser.refresh();

        assertEquals(100.0, browser.getZoomPercent());
    }

    @Test
    void resetReturnsToFullSize() {
        var browser = new WebviewZoomManager(preferences, rendered::add);
        browser.showSite("chat");
        browser.handleZoomCommand("down");

        browser.handleZoomCommand("reset");

        assertEquals(100.0, browser.getZoomPercent());
        assertEquals(0.0, preferences.getSiteZoomLevel("chat"));
    }
}
