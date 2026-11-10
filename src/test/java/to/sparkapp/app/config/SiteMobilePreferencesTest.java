package to.sparkapp.app.config;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SiteMobilePreferencesTest {
    @TempDir
    Path directory;

    @Test
    void theMobileVersionIsStoredOnlyForSitesThatUseIt() throws Exception {
        var file = directory.resolve("settings.json");
        var preferences = new AppPreferences(file.toFile());
        preferences.setSiteMobile("instagram", true);
        preferences.setSiteMobile("chat", true);
        preferences.setSiteMobile("chat", false);
        preferences.flush();

        var restored = new AppPreferences(file.toFile());

        assertTrue(restored.isSiteMobile("instagram"));
        assertFalse(restored.isSiteMobile("chat"));
        assertFalse(Files.readString(file).contains("chat"));
    }
}
