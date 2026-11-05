package to.sparkapp.app.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class SiteHistoryTest {
    @TempDir Path directory;
    private final AiConfiguration.AiConfig first = new AiConfiguration.AiConfig("first", "First", "https://example.com", null, null);
    private final AiConfiguration.AiConfig second = new AiConfiguration.AiConfig("second", "Second", "https://example.com", null, null);

    @Test void remembersIndependentPagesByIdAndRestoresThemFromDisk() {
        var file = directory.resolve("settings.json").toFile();
        var preferences = new AppPreferences(file);
        preferences.setRememberLastAi(true);
        preferences.rememberSitePage(first, "https://example.com/chat/first", true);
        preferences.rememberSitePage(second, "https://example.com/chat/second", false);
        preferences.flush();
        var restored = new AppPreferences(file);
        assertEquals("first", restored.getLastSiteId());
        assertEquals("https://example.com/chat/first", restored.getLastUrl());
        assertEquals("https://example.com/chat/second", restored.getSiteUrl(second));
    }

    @Test void changedHostNeverReceivesOldRememberedPage() {
        var preferences = new AppPreferences(directory.resolve("settings.json").toFile());
        preferences.setRememberLastAi(true);
        preferences.rememberSitePage(first, "https://example.com/chat/first", true);
        var changed = new AiConfiguration.AiConfig("first", "First", "https://other.test", null, null);
        assertNull(preferences.getSiteUrl(changed));
        preferences.rememberSitePage(first, "https://evil.test", true);
        assertEquals("https://example.com/chat/first", preferences.getSiteUrl(first));
        preferences.flush();
    }

    @Test void disablingHistoryClearsAllRememberedSites() {
        var preferences = new AppPreferences(directory.resolve("settings.json").toFile());
        preferences.setRememberLastAi(true);
        preferences.rememberSitePage(first, "https://example.com/chat/first", true);
        preferences.setRememberLastAi(false);
        preferences.setRememberLastAi(true);
        assertNull(preferences.getSiteUrl(first));
        assertNull(preferences.getLastSiteId());
        assertNull(preferences.getLastUrl());
    }
}
