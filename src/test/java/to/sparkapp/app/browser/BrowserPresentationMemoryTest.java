package to.sparkapp.app.browser;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.config.TestPreferences;

class BrowserPresentationMemoryTest {
    @TempDir
    Path directory;

    @Test
    void quickHideKeepsThePageReadyAndALongOneGivesMemoryBack() throws Exception {
        var presentation = new BrowserPresentation(preferences(true), Duration.ofMillis(300));

        presentation.hibernate();
        assertFalse(presentation.wantsLowMemory(), "A page hidden a moment ago must stay ready to show");

        Thread.sleep(400);
        assertTrue(presentation.wantsLowMemory());

        presentation.wakeup(0, true);
        assertFalse(presentation.wantsLowMemory(), "A shown page always uses normal memory");
    }

    @Test
    void withTheSettingOffMemoryIsNeverReduced() throws Exception {
        var presentation = new BrowserPresentation(preferences(false), Duration.ZERO);

        presentation.hibernate();
        Thread.sleep(50);

        assertFalse(presentation.wantsLowMemory());
    }

    @Test
    void hidingAgainRestartsTheWait() throws Exception {
        var presentation = new BrowserPresentation(preferences(true), Duration.ofMillis(300));

        presentation.hibernate();
        Thread.sleep(200);
        presentation.wakeup(0, true);
        presentation.hibernate();
        Thread.sleep(200);

        assertFalse(presentation.wantsLowMemory(), "Time hidden before the last show does not count");
    }

    private AppPreferences preferences(boolean reduce) {
        var preferences = TestPreferences.at(directory.resolve("settings.json").toFile());
        preferences.setBackgroundMemoryReductionEnabled(reduce);

        return preferences;
    }
}
