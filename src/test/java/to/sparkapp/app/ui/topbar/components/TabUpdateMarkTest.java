package to.sparkapp.app.ui.topbar.components;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
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
@Timeout(20)
class TabUpdateMarkTest {
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
}
