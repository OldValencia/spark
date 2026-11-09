package to.sparkapp.app.config;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipInputStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import to.sparkapp.app.utils.SystemUtils;

class SettingsExportTest {
    @TempDir
    Path directory;

    @Test
    void archiveHoldsSitesIconsAndPreferencesButNotTheOpenPages() throws Exception {
        var data = Files.createDirectories(directory.resolve("data"));
        Files.writeString(data.resolve("app-config.json"), """
                {"alwaysOnTop": false, "zoomEnabled": true, "lastSiteId": "chat",
                 "lastUrl": "https://chat.example/c/private", "siteUrls": {"chat": "https://chat.example/c/private"}}
                """);
        Files.writeString(data.resolve("providers.json"), "[{\"id\":\"chat\"}]");
        Files.createDirectories(data.resolve("icons"));
        Files.writeString(data.resolve("icons/chat.svg"), "<svg/>");
        Files.writeString(data.resolve("app-config.json.bak"), "old");
        var target = directory.resolve("spark-settings.zip");

        new SettingsExport(data).writeTo(target);

        var entries = read(target);
        assertEquals(Set.of("app-config.json", "providers.json", "icons/chat.svg", "README.txt"),
                entries.keySet());
        assertEquals("[{\"id\":\"chat\"}]", entries.get("providers.json"));
        assertEquals("<svg/>", entries.get("icons/chat.svg"));

        var config = new ObjectMapper().readTree(entries.get("app-config.json"));
        assertFalse(config.get("alwaysOnTop").asBoolean());
        assertTrue(config.has("zoomEnabled"));
        assertFalse(config.has("siteUrls") || config.has("lastUrl") || config.has("lastSiteId"));
        assertFalse(entries.get("app-config.json").contains("private"));

        var readme = entries.get("README.txt");
        assertTrue(readme.contains(SettingsExport.restoreFolder()));
        assertTrue(readme.contains("Exit Spark"));
    }

    @Test
    void restoreFolderFitsAnotherUsersPc() {
        if (SystemUtils.isWindows() && System.getProperty("spark.dataDir") == null) {
            assertEquals("%USERPROFILE%\\Documents\\OldValencia\\Spark", SettingsExport.restoreFolder());
        }
    }

    @Test
    void failedExportLeavesNoPartialFile() throws Exception {
        var data = Files.createDirectories(directory.resolve("data"));
        Files.writeString(data.resolve("app-config.json"), "[\"not an object\"]");
        var target = directory.resolve("broken.zip");

        assertThrows(RuntimeException.class, () -> new SettingsExport(data).writeTo(target));
        assertFalse(Files.exists(target));
    }

    private static Map<String, String> read(Path archive) throws IOException {
        var entries = new HashMap<String, String>();

        try (var zip = new ZipInputStream(Files.newInputStream(archive))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                entries.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }

        return entries;
    }
}
