package to.sparkapp.app.ui.topbar.components;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.stream.Stream;

import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
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
class AiDockRevealTest {
    @TempDir
    Path directory;

    @Test
    void theOpenSiteIsScrolledIntoViewWhenTheTabsAreFirstLaidOut() throws Exception {
        var preferences = TestPreferences.at(directory.resolve("settings.json").toFile());
        var stage = JavaFxTestRuntime.call(Stage::new);
        var browser = JavaFxTestRuntime.call(() -> new FxWebViewPane("about:blank", preferences));
        var sites = Stream.of("one", "two", "three", "four", "five", "six", "seven", "eight")
                .map(id -> new AiConfig(id, "Site " + id, "https://example.com/" + id, null, null))
                .toList();
        var dock = JavaFxTestRuntime.call(() -> {
            var created = new AiDock(sites, browser, preferences, "six");
            created.setPrefWidth(260);
            created.setMaxWidth(260);
            stage.setScene(new Scene(new HBox(created), 320, 60));
            stage.show();

            return created;
        });

        try {
            Thread.sleep(400);

            JavaFxTestRuntime.call(() -> {
                var tab = dock.getSelectedNode();
                var visible = dock.getScrollPane().localToScreen(dock.getScrollPane().getBoundsInLocal());
                var shown = tab.localToScreen(tab.getBoundsInLocal());

                assertEquals("six", tab.getConfig().id());
                assertTrue(shown.getMinX() >= visible.getMinX() - 1 && shown.getMaxX() <= visible.getMaxX() + 1,
                        "The tab of the open site is in view: " + shown + " within " + visible);

                return null;
            });
        } finally {
            JavaFxTestRuntime.call(() -> {
                dock.dispose();
                stage.close();
                browser.shutdown(() -> {
                });

                return null;
            });
        }
    }
}
