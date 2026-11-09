package to.sparkapp.app.config;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TutorialPreferencesTest {
    @TempDir
    Path directory;

    @Test
    void newUserSeesTheTourOnce() {
        var file = directory.resolve("settings.json").toFile();
        var preferences = new AppPreferences(file);

        assertTrue(preferences.isTutorialPending());

        preferences.setTutorialDone(true);

        assertFalse(new AppPreferences(file).isTutorialPending());
    }

    @Test
    void existingUserWithoutAShortcutSeesTheTour() throws Exception {
        var file = directory.resolve("settings.json");
        Files.writeString(file, "{\"alwaysOnTop\": true}");

        assertTrue(new AppPreferences(file.toFile()).isTutorialPending());
    }

    @Test
    void existingUserWithAShortcutIsNotInterrupted() throws Exception {
        var file = directory.resolve("settings.json");
        Files.writeString(file, "{\"hotkeyToStartApplication\": [29, 57]}");

        assertFalse(new AppPreferences(file.toFile()).isTutorialPending());
        assertFalse(new AppPreferences(file.toFile()).isTutorialPending(), "The decision is kept");
    }
}
