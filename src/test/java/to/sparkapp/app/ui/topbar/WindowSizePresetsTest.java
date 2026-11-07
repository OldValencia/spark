package to.sparkapp.app.ui.topbar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.imageio.ImageIO;

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.config.WindowSizePreset;
import to.sparkapp.app.config.WindowSizePreset.Slot;
import to.sparkapp.app.support.JavaFxTestRuntime;
import to.sparkapp.app.ui.dialogs.PresetNameDialog;
import to.sparkapp.app.windows.WindowController;

@Tag("native")
@Timeout(20)
class WindowSizePresetsTest {
    @Test
    void renameDialogRejectsBlankNamesAndPreservesDimensions() throws Exception {
        JavaFxTestRuntime.call(() -> {
            var preferences = new AppPreferences();
            var previousPreset = preferences.getWindowSizePreset(Slot.SMALL);
            var frame = new Stage();
            frame.setScene(new Scene(new StackPane(), 400, 300));
            frame.setX(-20000);
            frame.setY(-20000);
            var failure = new AtomicReference<Throwable>();
            preferences.setWindowSizePreset(Slot.SMALL, new WindowSizePreset("Chat", 900, 650));
            var controller = new WindowController(frame, preferences);
            var previousPlacement = preferences.getWindowPlacement();

            try {
                frame.show();
                var presets = new WindowSizePresets(frame, preferences, controller, () -> {
                });
                var button = (WindowSizePresetButton) presets.getChildren().getFirst();
                Platform.runLater(() -> {
                    Window dialog = null;

                    try {
                        dialog = Window.getWindows().stream().filter(window -> window instanceof PresetNameDialog &&
                                window.isShowing()).findFirst().orElseThrow();
                        assertEquals(StageStyle.TRANSPARENT, ((Stage) dialog).getStyle());
                        assertEquals(Color.TRANSPARENT, dialog.getScene().getFill());
                        var pane = dialog.getScene().getRoot();
                        var preview = Path.of("build", "previews", "preset-name-dialog.png");
                        Files.createDirectories(preview.getParent());
                        ImageIO.write(SwingFXUtils.fromFXImage(dialog.getScene().snapshot(null), null), "png",
                                preview.toFile());
                        var editor = (TextField) pane.lookup(".text-field");
                        var confirm = (Button) pane.lookup(".primary-button");
                        editor.setText(" ");
                        assertTrue(confirm.isDisabled());
                        editor.setText("Reading");
                        confirm.fire();
                    } catch (Throwable error) {
                        failure.set(error);
                    } finally {
                        if (dialog != null) {
                            dialog.hide();
                        }
                    }
                });
                button.getItems().get(1).fire();
                assertNull(failure.get());
                assertEquals("Reading", button.getText());
                assertEquals(new WindowSizePreset("Reading", 900, 650),
                        new AppPreferences().getWindowSizePreset(Slot.SMALL));
            } finally {
                controller.dispose();
                frame.close();
                preferences.setWindowSizePreset(Slot.SMALL, previousPreset);
                preferences.setWindowPlacement(previousPlacement);
                preferences.flush();
            }

            return null;
        });
    }

    @Test
    void savingApplyingAndResettingKeepTheOriginalPresetOnSmallerScreens() throws Exception {
        JavaFxTestRuntime.call(() -> {
            var preferences = new AppPreferences();
            var previousPreset = preferences.getWindowSizePreset(Slot.SMALL);
            var previousPlacement = preferences.getWindowPlacement();
            var frame = new Stage();
            frame.setScene(new Scene(new StackPane()));
            var controller = new WindowController(frame, preferences);
            var dismissed = new AtomicInteger();

            try {
                var presets = new WindowSizePresets(frame, preferences, controller, dismissed::incrementAndGet);
                var button = (WindowSizePresetButton) presets.getChildren().getFirst();
                controller.preset(760, 540);
                button.getItems().getFirst().fire();
                assertEquals(Math.round(frame.getWidth()), preferences.getWindowSizePreset(Slot.SMALL).width());
                assertEquals(Math.round(frame.getHeight()), preferences.getWindowSizePreset(Slot.SMALL).height());

                var large = new WindowSizePreset("A custom size with a long name", 20000, 20000);
                preferences.setWindowSizePreset(Slot.SMALL, large);
                var restored = new WindowSizePresets(frame, new AppPreferences(), controller, dismissed::incrementAndGet);
                var restoredButton = (WindowSizePresetButton) restored.getChildren().getFirst();
                assertEquals(large.name(), restoredButton.getText());
                assertTrue(restoredButton.getTooltip().getText().contains("20000 × 20000"));
                frame.getScene().setRoot(restored);
                frame.getScene().getStylesheets().add(getClass().getResource("/css/shell.css").toExternalForm());
                restored.applyCss();
                restored.resize(248, 48);
                restored.layout();
                for (var child : restored.getChildren()) {
                    assertEquals((248.0 - 12) / 3, child.getLayoutBounds().getWidth(), 1);
                }
                restoredButton.fire();
                var bounds = Screen.getPrimary().getVisualBounds();
                assertTrue(frame.getWidth() <= bounds.getWidth());
                assertTrue(frame.getHeight() <= bounds.getHeight());
                assertEquals(large, new AppPreferences().getWindowSizePreset(Slot.SMALL));
                assertEquals(1, dismissed.get());

                restoredButton.getItems().getLast().fire();
                assertEquals(Slot.SMALL.getDefaultPreset(), new AppPreferences().getWindowSizePreset(Slot.SMALL));
                assertEquals("S", restoredButton.getText());
            } finally {
                controller.dispose();
                frame.close();
                preferences.setWindowSizePreset(Slot.SMALL, previousPreset);
                preferences.setWindowPlacement(previousPlacement);
                preferences.flush();
            }

            return null;
        });
    }
}
