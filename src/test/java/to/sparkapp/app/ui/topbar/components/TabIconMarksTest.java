package to.sparkapp.app.ui.topbar.components;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import to.sparkapp.app.config.AiConfiguration.AiConfig;
import to.sparkapp.app.config.TestPreferences;
import to.sparkapp.app.support.JavaFxTestRuntime;
import to.sparkapp.app.ui.webview.FxWebViewPane;

@Tag("native")
@Timeout(30)
class TabIconMarksTest {
    @TempDir
    Path directory;

    @Test
    void dotMarksOtherSitesAndClearsWhenTheSiteIsOpened() throws Exception {
        JavaFxTestRuntime.call(() -> {
            var preferences = TestPreferences.at(directory.resolve("settings.json").toFile());
            var browser = new FxWebViewPane("about:blank", preferences);
            var sites = Stream.of("one", "two").map(id -> new AiConfig(id, id, "https://example.com/" + id, null, null))
                    .toList();
            var dock = new AiDock(sites, browser, preferences);

            try {
                var first = dock.getDockItems().getFirst();
                var second = dock.getDockItems().getLast();
                assertSame(first, dock.getSelectedNode());

                dock.markUpdated("one");
                assertFalse(first.isUpdated(), "The open site never gets a dot");

                dock.markUpdated("two");
                assertTrue(second.isUpdated());
                assertEquals("two, updated", second.getAccessibleText());

                dock.selectItem(second);
                assertFalse(second.isUpdated(), "Opening the site clears its dot");
                assertEquals("two", second.getAccessibleText());
            } finally {
                dock.dispose();
                browser.shutdown(() -> {
                });
            }

            return null;
        });
    }

    @Test
    void aSpeakerShowsWhileTheSiteSoundsAndStaysAMomentAfterItStops() throws Exception {
        var preferences = TestPreferences.at(directory.resolve("settings.json").toFile());
        var browser = JavaFxTestRuntime.call(() -> new FxWebViewPane("about:blank", preferences));
        var dock = JavaFxTestRuntime.call(() -> new AiDock(List.of(new AiConfig("one", "one", "https://example.com/",
                null, null)), browser, preferences));
        var tab = JavaFxTestRuntime.call(() -> dock.getDockItems().getFirst());

        try {
            JavaFxTestRuntime.call(() -> {
                assertFalse(tab.isShowingSound(), "A quiet site has no speaker");

                tab.showSound(true, false);
                assertTrue(tab.isShowingSound());
                assertEquals("one, playing sound", tab.getAccessibleText());

                tab.showSound(true, true);
                assertTrue(tab.isShowingMuted(), "A muted site that plays gets a crossed-out speaker");
                assertEquals("one, muted", tab.getAccessibleText());

                tab.showSound(false, true);
                assertTrue(tab.isShowingMuted(), "The speaker stays a moment after the sound stops");

                return null;
            });

            Thread.sleep((long) TabSoundBadge.QUIET_DELAY.toMillis() + 500);

            JavaFxTestRuntime.call(() -> {
                assertFalse(tab.isShowingMuted());
                assertFalse(tab.isShowingSound());

                return null;
            });
        } finally {
            JavaFxTestRuntime.call(() -> {
                dock.dispose();
                browser.shutdown(() -> {
                });

                return null;
            });
        }
    }
}
