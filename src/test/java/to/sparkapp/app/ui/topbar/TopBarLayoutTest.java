package to.sparkapp.app.ui.topbar;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;

import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import to.sparkapp.app.config.AiConfiguration;
import to.sparkapp.app.config.TestPreferences;
import to.sparkapp.app.support.JavaFxTestRuntime;
import to.sparkapp.app.ui.webview.FxWebViewPane;
import to.sparkapp.app.windows.WindowController;

@Tag("native")
@Timeout(30)
class TopBarLayoutTest {
    @TempDir
    Path directory;

    @Test
    void hiddenMascotGivesItsPlaceToTheTabs() throws Exception {
        JavaFxTestRuntime.call(() -> {
            var preferences = TestPreferences.at(directory.resolve("settings.json").toFile());
            preferences.setMascotEnabled(true);
            var stage = new Stage();
            var controller = new WindowController(stage, preferences);
            var browser = new FxWebViewPane("about:blank", preferences);
            var bar = new TopBarArea(new AiConfiguration(preferences), browser, stage, preferences, () -> {
            }, () -> {
            }, controller, () -> {
            });
            stage.setScene(new Scene(bar, 700, TopBarArea.HEIGHT));

            try {
                bar.applyCss();
                bar.layout();
                var withMascot = bar.getDock().getLayoutX();
                var widthWithMascot = bar.getDock().getPrefWidth();

                preferences.setMascotEnabled(false);
                bar.refreshAppearance();
                bar.applyCss();
                bar.layout();

                assertTrue(withMascot - bar.getDock().getLayoutX() >= 40, "Tabs must move into the mascot's place");
                assertTrue(bar.getDock().getPrefWidth() >= widthWithMascot, "Tabs may use the freed room");

                preferences.setMascotEnabled(true);
                bar.refreshAppearance();
                bar.layout();

                assertEquals(withMascot, bar.getDock().getLayoutX(), 0.5);
            } finally {
                bar.dispose();
                controller.dispose();
                browser.shutdown(() -> {
                });
                stage.close();
            }

            return null;
        });
    }
}
