package to.sparkapp.app.ui.topbar;

import static org.junit.jupiter.api.Assertions.*;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.event.ActionEvent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import to.sparkapp.app.config.AiConfiguration.AiConfig;
import to.sparkapp.app.support.JavaFxTestRuntime;
import to.sparkapp.app.ui.dialogs.ProviderEditDialog;
import to.sparkapp.app.ui.dialogs.components.ProviderFormPanel;

@Tag("native")
@Timeout(30)
class ShellComponentsTest {
    private final AiConfig site = new AiConfig("site", "Example", "https://example.com", "#123456", null, "manual");

    @Test
    void choosingIconColorReplacesAnExplicitManualChoice() throws Exception {
        JavaFxTestRuntime.call(() -> {
            var form = new ProviderFormPanel(site);
            var scene = new Scene(form);
            scene.getRoot().applyCss();

            var picker = (ColorPicker) form.lookup(".color-picker");
            picker.setValue(Color.CORAL);
            picker.fireEvent(new ActionEvent());
            assertTrue(form.isManualColor());
            assertFalse(form.isUseIconColor());
            assertEquals("#FF7F50", form.getColorValue());

            var automatic = form.lookupAll(".button").stream()
                    .filter(node -> node instanceof Button button && "Use icon color".equals(button.getText()))
                    .map(Button.class::cast).findFirst().orElseThrow();
            automatic.fire();
            assertFalse(form.isManualColor());
            assertTrue(form.isUseIconColor());
            assertNull(form.getColorValue());

            return null;
        });
    }

    @Test
    void removingASiteRequiresTwoClicks() throws Exception {
        JavaFxTestRuntime.call(() -> {
            var owner = new Stage();
            var dialog = new ProviderEditDialog(owner, site);

            try {
                dialog.getScene().getRoot().applyCss();

                var remove = dialog.getScene().getRoot().lookupAll(".button").stream()
                        .filter(node -> node instanceof Button button && "Remove site".equals(button.getText()))
                        .map(Button.class::cast).findFirst().orElseThrow();
                remove.fire();
                assertFalse(dialog.isDeleted());
                assertFalse(dialog.isConfirmed());
                remove.fire();
                assertTrue(dialog.isDeleted());
                assertTrue(dialog.isConfirmed());
            } finally {
                dialog.close();
                owner.close();
            }

            return null;
        });
    }

    @Test
    void explicitlyDismissedSettingsMenuStaysClosedAfterItsQueuedRefresh() throws Exception {
        var owner = JavaFxTestRuntime.call(() -> {
            var stage = new Stage();
            stage.setScene(new Scene(new StackPane(new Button("Anchor")), 400, 200));
            stage.setX(-20000);
            stage.setY(-20000);
            stage.show();

            return stage;
        });
        var menu = JavaFxTestRuntime.call(() -> new ShellPopover("SETTINGS", 250));

        try {
            JavaFxTestRuntime.call(() -> {
                var enabled = new SimpleBooleanProperty(false);
                var row = menu.setting("pin", "Always on top", enabled, () -> enabled.set(!enabled.get()));
                menu.toggle(owner.getScene().getRoot());
                assertTrue(menu.isShowing());
                row.fire();
                assertTrue(enabled.get());
                assertEquals("Always on top: On", row.getAccessibleText());
                menu.dismiss();

                return null;
            });
            JavaFxTestRuntime.call(() -> {
                assertFalse(menu.isShowing());

                return null;
            });
        } finally {
            JavaFxTestRuntime.call(() -> {
                menu.dismiss();
                owner.close();

                return null;
            });
        }
    }
}
