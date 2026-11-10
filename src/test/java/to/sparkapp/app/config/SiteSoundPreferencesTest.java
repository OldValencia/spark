package to.sparkapp.app.config;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SiteSoundPreferencesTest {
    @TempDir
    Path directory;

    @Test
    void sitesFollowTheSettingForAllSitesUntilOneIsChanged() {
        var preferences = new AppPreferences(directory.resolve("settings.json").toFile());

        assertFalse(preferences.isSiteMuted("first", MuteRule.ALWAYS));

        preferences.setMuteAll(MuteRule.WHILE_HIDDEN, true);
        preferences.setSiteMuted("first", MuteRule.WHILE_HIDDEN, false);

        assertFalse(preferences.isSiteMuted("first", MuteRule.WHILE_HIDDEN));
        assertTrue(preferences.isSiteMuted("second", MuteRule.WHILE_HIDDEN));
        assertTrue(preferences.isSiteMuted("added later", MuteRule.WHILE_HIDDEN));
        assertFalse(preferences.isSiteMuted("second", MuteRule.ALWAYS));
    }

    @Test
    void changingTheSettingForAllSitesOverridesEverySite() {
        var preferences = new AppPreferences(directory.resolve("settings.json").toFile());
        preferences.setSiteMuted("first", MuteRule.ALWAYS, true);
        preferences.setSiteMuted("second", MuteRule.WHILE_HIDDEN, true);

        preferences.setMuteAll(MuteRule.ALWAYS, false);

        assertFalse(preferences.isSiteMuted("first", MuteRule.ALWAYS));
        assertTrue(preferences.isSiteMuted("second", MuteRule.WHILE_HIDDEN), "The other rule is kept");
    }

    @Test
    void siteChoicesSurviveARestartWithoutStoringDefaults() throws Exception {
        var file = directory.resolve("settings.json");
        var preferences = new AppPreferences(file.toFile());
        preferences.setSiteMuted("first", MuteRule.ALWAYS, true);
        preferences.setSiteMuted("second", MuteRule.ALWAYS, true);
        preferences.setSiteMuted("second", MuteRule.ALWAYS, false);
        preferences.flush();

        var restored = new AppPreferences(file.toFile());

        assertTrue(restored.isSiteMuted("first", MuteRule.ALWAYS));
        assertFalse(restored.isSiteMuted("second", MuteRule.ALWAYS));
        assertFalse(Files.readString(file).contains("second"));
    }

    @Test
    void zoomAndSoundOfOneSiteAreKeptSeparately() {
        var preferences = new AppPreferences(directory.resolve("settings.json").toFile());
        preferences.setSiteZoomLevel("first", 1.0);
        preferences.setSiteMuted("first", MuteRule.ALWAYS, true);

        preferences.setZoomEnabled(false);

        assertEquals(0.0, preferences.getSiteZoomLevel("first"));
        assertTrue(preferences.isSiteMuted("first", MuteRule.ALWAYS));
    }
}
