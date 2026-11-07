package to.sparkapp.app.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import to.sparkapp.app.config.WindowSizePreset.Slot;

class WindowSizePresetTest {
    @TempDir
    Path directory;

    @Test
    void presetsSurviveRestartAndResetOnlyTheSelectedSlot() {
        var file = directory.resolve("settings.json").toFile();
        var preferences = new AppPreferences(file);
        var chat = new WindowSizePreset("  Chat  ", 930, 740);
        var reading = new WindowSizePreset("Reading", 1150, 900);
        preferences.setAlwaysOnTop(false);
        preferences.setWindowSizePreset(Slot.SMALL, chat);
        preferences.setWindowSizePreset(Slot.LARGE, reading);

        var restored = new AppPreferences(file);
        assertEquals(new WindowSizePreset("Chat", 930, 740), restored.getWindowSizePreset(Slot.SMALL));
        assertEquals(reading, restored.getWindowSizePreset(Slot.LARGE));
        assertEquals(Slot.MEDIUM.getDefaultPreset(), restored.getWindowSizePreset(Slot.MEDIUM));
        assertFalse(restored.isAlwaysOnTop());
        restored.resetWindowSizePreset(Slot.SMALL);

        var reset = new AppPreferences(file);
        assertEquals(Slot.SMALL.getDefaultPreset(), reset.getWindowSizePreset(Slot.SMALL));
        assertEquals(reading, reset.getWindowSizePreset(Slot.LARGE));
    }

    @Test
    void invalidStoredPresetFallsBackWithoutLosingOtherSettings() throws Exception {
        var file = directory.resolve("settings.json");
        Files.writeString(file, """
                {"alwaysOnTop":false,"windowSizePresets":{
                  "SMALL":{"name":"Broken","width":-1,"height":580},
                  "LARGE":{"name":"Reading","width":1100,"height":800}
                }}
                """);
        var preferences = new AppPreferences(file.toFile());
        assertEquals(Slot.SMALL.getDefaultPreset(), preferences.getWindowSizePreset(Slot.SMALL));
        assertEquals(new WindowSizePreset("Reading", 1100, 800), preferences.getWindowSizePreset(Slot.LARGE));
        assertFalse(preferences.isAlwaysOnTop());
    }

    @Test
    void invalidEditsDoNotOverwriteTheSavedPreset() {
        var preferences = new AppPreferences(directory.resolve("settings.json").toFile());
        var chat = new WindowSizePreset("Chat", 800, 600);
        preferences.setWindowSizePreset(Slot.SMALL, chat);

        for (var invalid : new WindowSizePreset[]{new WindowSizePreset(" ", 800, 600),
                new WindowSizePreset("a".repeat(33), 800, 600), new WindowSizePreset("Chat\nSmall", 800, 600),
                new WindowSizePreset("Chat", 0, 600)}) {
            assertThrows(IllegalArgumentException.class, () -> preferences.setWindowSizePreset(Slot.SMALL, invalid));
        }
        assertEquals(chat, preferences.getWindowSizePreset(Slot.SMALL));
    }
}
