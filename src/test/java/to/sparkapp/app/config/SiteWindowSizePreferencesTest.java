package to.sparkapp.app.config;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import to.sparkapp.app.config.WindowSizePreset.Slot;

class SiteWindowSizePreferencesTest {
    @TempDir
    Path directory;

    @Test
    void eachKindOfSiteSizeSurvivesARestart() {
        var file = directory.resolve("settings.json").toFile();
        var preferences = new AppPreferences(file);
        preferences.setSiteWindowSize("instagram", SiteWindowSize.custom(480.4, 900.6));
        preferences.setSiteWindowSize("chat", SiteWindowSize.ofPreset(Slot.LARGE));
        preferences.setSiteWindowSize("docs", SiteWindowSize.filling());
        preferences.flush();

        var restored = new AppPreferences(file);

        assertEquals(new SiteWindowSize(null, false, 480, 901), restored.getSiteWindowSize("instagram"));
        assertEquals(SiteWindowSize.ofPreset(Slot.LARGE), restored.getSiteWindowSize("chat"));
        assertEquals(SiteWindowSize.filling(), restored.getSiteWindowSize("docs"));
        assertNull(restored.getSiteWindowSize("other"), "Other sites use the shared size");
    }

    @Test
    void aSiteThatUsesTheSharedSizeAgainLeavesNothingBehind() throws Exception {
        var file = directory.resolve("settings.json");
        var preferences = new AppPreferences(file.toFile());
        preferences.setSiteWindowSize("instagram", SiteWindowSize.custom(480, 900));
        preferences.setSiteWindowSize("instagram", null);
        preferences.flush();

        assertNull(new AppPreferences(file.toFile()).getSiteWindowSize("instagram"));
        assertFalse(Files.readString(file).contains("instagram"));
    }
}
