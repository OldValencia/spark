package to.sparkapp.app.ui.topbar;

import java.util.List;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import to.sparkapp.app.config.AiConfiguration.AiConfig;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.support.JavaFxTestRuntime;
import to.sparkapp.app.ui.webview.FxWebViewPane;
import to.sparkapp.app.windows.WindowController;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("native")
@Timeout(20)
class WindowMenuSettingsTest {
    @Test
    void pillAndMotionPreferencesAreRemovedFromWindowMenus() throws Exception {
        JavaFxTestRuntime.call(() -> {
            var preferences = new AppPreferences();
            var stage = new Stage();
            var controller = new WindowController(stage, preferences);
            var browser = new FxWebViewPane("about:blank", preferences);
            var sizes = new WindowSizeMenu(stage, preferences, controller, () -> null);
            var options = new WindowOptionsMenu(preferences, () -> {
            });
            var movedSettings = List.of("Auto-hide top-edge pill", "Quick upward gesture", "Open on top-edge hover",
                    "Reduce motion");

            try {
                var optionRows = options.getContent().getFirst().lookupAll(".shell-row").stream()
                        .map(Button.class::cast)
                        .map(Button::getText)
                        .toList();
                assertEquals(List.of("Settings", "Extensions"), optionRows, "Site actions live in the tab menu");

                for (var menu : List.of(sizes, options)) {
                    var labels = menu.getContent().getFirst().lookupAll(".button").stream().filter(
                            Button.class::isInstance).map(Button.class::cast).map(
                            button -> String.valueOf(button.getAccessibleText()) + " " + button.getText()).toList();
                    assertTrue(labels.stream().noneMatch(label -> movedSettings.stream().anyMatch(label::contains)));
                }
            } finally {
                sizes.dismiss();
                options.dismiss();
                controller.dispose();
                browser.shutdown(() -> {
                });
                stage.close();
                preferences.flush();
            }
            return null;
        });
    }

    @Test
    void theSizeMenuRemembersASizeForTheSiteOnScreen() throws Exception {
        JavaFxTestRuntime.call(() -> {
            var preferences = new AppPreferences();
            var placement = preferences.getWindowPlacement();
            var site = new AiConfig("size-menu-test", "Instagram", "https://example.com/", "#123456", null);
            var anchor = new Button("Size");
            var stage = new Stage();
            stage.setScene(new Scene(new StackPane(anchor), 500, 300));
            var controller = new WindowController(stage, preferences);
            stage.show();
            var sizes = new WindowSizeMenu(stage, preferences, controller, () -> site);

            try {
                sizes.toggle(anchor);
                var row = (Button) sizes.getContent().getFirst().lookupAll(".shell-row").stream()
                        .filter(node -> String.valueOf(((Button) node).getAccessibleText()).startsWith("Remember size"))
                        .findFirst()
                        .orElseThrow();

                assertEquals("Remember size for Instagram: Off", row.getAccessibleText());

                row.fire();

                assertTrue(controller.hasOwnSize(site.id()));
                assertTrue(sizes.isShowing(), "Switching the setting keeps the menu open");

                row.fire();

                assertFalse(controller.hasOwnSize(site.id()));
            } finally {
                sizes.dismiss();
                controller.dispose();
                stage.close();
                preferences.setSiteWindowSize(site.id(), null);
                preferences.setWindowPlacement(placement);
                preferences.flush();
            }
            return null;
        });
    }
}
