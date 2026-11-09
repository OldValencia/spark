package to.sparkapp.app.config;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TopEdgePreferencesTest {
    @TempDir
    Path directory;

    @Test
    void newProfilesKeepThePillVisibleAndQuickOpeningOff() {
        var preferences = new AppPreferences(directory.resolve("new.json").toFile());

        assertTrue(preferences.isTopPill());
        assertFalse(preferences.isFastTopEdgeReveal());
        assertTrue(preferences.isTopEdgeQuietInFullscreen());
    }

    @Test
    void olderProfilesWithoutTheNewOptionsHaveTheSameDefaults() throws Exception {
        var file = directory.resolve("legacy.json");
        Files.writeString(file, "{\"rememberLastAi\":false}");
        var preferences = new AppPreferences(file.toFile());

        assertTrue(preferences.isTopPill());
        assertFalse(preferences.isFastTopEdgeReveal());
        assertTrue(preferences.isTopEdgeQuietInFullscreen());
        assertFalse(preferences.isRememberLastAi());
    }

    @Test
    void topEdgeOverFullscreenAppsCanBeTurnedBackOn() {
        var file = directory.resolve("preferences.json").toFile();
        var preferences = new AppPreferences(file);

        preferences.setTopEdgeQuietInFullscreen(false);
        preferences.flush();
        assertFalse(new AppPreferences(file).isTopEdgeQuietInFullscreen());

        preferences.setTopEdgeQuietInFullscreen(true);
        preferences.flush();
        assertTrue(new AppPreferences(file).isTopEdgeQuietInFullscreen());
    }

    @Test
    void quickOpeningRequiresAutoHideAndPersistsOnlyWhenEnabled() {
        var file = directory.resolve("preferences.json").toFile();
        var preferences = new AppPreferences(file);
        preferences.setFastTopEdgeReveal(true);
        assertFalse(preferences.isFastTopEdgeReveal());

        preferences.setTopPill(false);
        assertFalse(preferences.isFastTopEdgeReveal());
        preferences.setFastTopEdgeReveal(true);
        preferences.flush();
        assertTrue(new AppPreferences(file).isFastTopEdgeReveal());

        preferences.setFastTopEdgeReveal(false);
        preferences.flush();
        assertFalse(new AppPreferences(file).isFastTopEdgeReveal());
    }

    @Test
    void keepingThePillVisibleClearsQuickOpeningUntilTheUserOptsInAgain() {
        var file = directory.resolve("preferences.json").toFile();
        var preferences = new AppPreferences(file);
        preferences.setTopPill(false);
        preferences.setFastTopEdgeReveal(true);
        preferences.setTopPill(true);
        preferences.flush();

        var restored = new AppPreferences(file);
        assertTrue(restored.isTopPill());
        assertFalse(restored.isFastTopEdgeReveal());
        restored.setTopPill(false);
        assertFalse(restored.isFastTopEdgeReveal());
        restored.flush();
    }
}
