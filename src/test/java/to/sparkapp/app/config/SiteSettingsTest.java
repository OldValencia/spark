package to.sparkapp.app.config;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import to.sparkapp.app.config.AiConfiguration.AiConfig;

class SiteSettingsTest {
    @TempDir
    Path directory;

    private final AiConfig first = new AiConfig("first", "First", "https://first.test", null, null);
    private final AiConfig second = new AiConfig("second", "Second", "https://second.test", null, null);

    @Test
    void eachSiteKeepsItsOwnZoomAfterRestart() {
        var file = directory.resolve("settings.json").toFile();
        var preferences = new AppPreferences(file);
        preferences.setSiteZoomLevel("first", 1.0);
        preferences.setSiteZoomLevel("second", -0.5);
        preferences.flush();

        var restored = new AppPreferences(file);

        assertEquals(1.0, restored.getSiteZoomLevel("first"));
        assertEquals(-0.5, restored.getSiteZoomLevel("second"));
        assertEquals(0.0, restored.getSiteZoomLevel("unknown"));
    }

    @Test
    void defaultZoomIsNotWrittenToTheFile() throws Exception {
        var file = directory.resolve("settings.json");
        var preferences = new AppPreferences(file.toFile());
        preferences.setSiteZoomLevel("first", 1.0);
        preferences.setSiteZoomLevel("first", 0.0);
        preferences.flush();

        assertFalse(Files.readString(file).contains("siteSettings"));
    }

    @Test
    void switchingZoomOffResetsEverySiteAndIgnoresChanges() {
        var file = directory.resolve("settings.json").toFile();
        var preferences = new AppPreferences(file);
        preferences.setSiteZoomLevel("first", 1.0);
        preferences.setSiteZoomLevel("second", 2.0);

        preferences.setZoomEnabled(false);
        preferences.setSiteZoomLevel("first", 1.5);

        assertEquals(0.0, preferences.getSiteZoomLevel("first"));
        assertEquals(0.0, preferences.getSiteZoomLevel("second"));

        preferences.setZoomEnabled(true);
        preferences.setSiteZoomLevel("first", 0.5);
        preferences.flush();

        assertEquals(0.5, new AppPreferences(file).getSiteZoomLevel("first"));
    }

    @Test
    void removedSitesLoseTheirSettings() {
        var file = directory.resolve("settings.json").toFile();
        var preferences = new AppPreferences(file);
        preferences.setSiteZoomLevel("first", 1.0);
        preferences.setSiteZoomLevel("second", 1.5);

        preferences.pruneSiteSettings(List.of(second));
        preferences.flush();

        var restored = new AppPreferences(file);
        assertEquals(0.0, restored.getSiteZoomLevel(first.id()));
        assertEquals(1.5, restored.getSiteZoomLevel(second.id()));
    }

    @Test
    void fileWithTheRemovedGlobalZoomStillLoads() throws Exception {
        var file = directory.resolve("settings.json");
        Files.writeString(file, """
                {
                  "lastZoomValue": 1.5,
                  "zoomEnabled": true,
                  "alwaysOnTop": false,
                  "lastSiteId": "first"
                }
                """);

        var preferences = new AppPreferences(file.toFile());

        assertFalse(preferences.isAlwaysOnTop());
        assertEquals("first", preferences.getLastSiteId());
        assertEquals(0.0, preferences.getSiteZoomLevel("first"));
        assertFalse(Files.readString(file).contains("lastZoomValue"));
    }
}
