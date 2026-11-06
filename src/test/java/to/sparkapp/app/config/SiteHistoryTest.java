package to.sparkapp.app.config;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import to.sparkapp.app.config.AiConfiguration.AiConfig;

class SiteHistoryTest {
    @Test
    void windowPreferencesAndSiteHistorySurviveTheSameSave() {
        var file = directory.resolve("window-settings.json").toFile();
        var preferences = new AppPreferences(file);
        var placement = new AppPreferences.WindowPlacement(true, 100, 0, 860, 700, 800, 600, 0, 0);
        preferences.setWindowPlacement(placement);
        preferences.setAlwaysOnTop(false);
        preferences.setReducedMotion(true);
        preferences.setAutoHide(true);
        preferences.setTopPill(false);
        preferences.setTopEdgeReveal(false);
        preferences.setRememberLastAi(true);
        preferences.rememberSitePage(first, "https://example.com/chat/restored", true);
        preferences.flush();

        var restored = new AppPreferences(file);
        assertEquals(placement, restored.getWindowPlacement());
        assertFalse(restored.isAlwaysOnTop());
        assertTrue(restored.isReducedMotion());
        assertTrue(restored.isAutoHide());
        assertFalse(restored.isTopPill());
        assertFalse(restored.isTopEdgeReveal());
        assertEquals("first", restored.getLastSiteId());
        assertEquals("https://example.com/chat/restored", restored.getSiteUrl(first));
    }

    @TempDir
    Path directory;
    private final AiConfig first = new AiConfig("first", "First", "https://example.com", null, null);
    private final AiConfig second = new AiConfig("second", "Second", "https://example.com", null, null);

    @Test
    void backgroundMemoryReductionDefaultsOnAndCanBeDisabledPersistently() {
        var file = directory.resolve("settings.json").toFile();
        var preferences = new AppPreferences(file);
        assertTrue(preferences.isBackgroundMemoryReductionEnabled());
        preferences.setBackgroundMemoryReductionEnabled(false);
        assertFalse(new AppPreferences(file).isBackgroundMemoryReductionEnabled());
        preferences.setBackgroundMemoryReductionEnabled(true);
        assertTrue(new AppPreferences(file).isBackgroundMemoryReductionEnabled());
    }

    @Test
    void remembersIndependentPagesByIdAndRestoresThemFromDisk() {
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

    @Test
    void changedHostNeverReceivesOldRememberedPage() {
        var preferences = new AppPreferences(directory.resolve("settings.json").toFile());
        preferences.setRememberLastAi(true);
        preferences.rememberSitePage(first, "https://example.com/chat/first", true);

        var changed = new AiConfig("first", "First", "https://other.test", null, null);
        assertNull(preferences.getSiteUrl(changed));
        preferences.rememberSitePage(first, "https://evil.test", true);
        assertEquals("https://example.com/chat/first", preferences.getSiteUrl(first));
        preferences.flush();
    }

    @Test
    void disablingHistoryClearsAllRememberedSites() {
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
