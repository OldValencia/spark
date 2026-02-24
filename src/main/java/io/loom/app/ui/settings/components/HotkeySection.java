package io.loom.app.ui.settings.components;

import io.loom.app.config.AppPreferences;
import io.loom.app.ui.Theme;
import io.loom.app.utils.GlobalHotkeyManager;
import io.loom.app.utils.SystemUtils;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;

import java.util.concurrent.atomic.AtomicReference;

public class HotkeySection extends VBox {

    public HotkeySection(AppPreferences appPreferences, GlobalHotkeyManager hotkeyManager) {
        this.setAlignment(Pos.CENTER_LEFT);
        this.setMaxWidth(Double.MAX_VALUE);

        var mainRow = new HBox();
        mainRow.setAlignment(Pos.CENTER_LEFT);
        mainRow.setMaxWidth(Double.MAX_VALUE);

        mainRow.getChildren().add(buildLabel("Toggle Window Shortcut"));

        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        mainRow.getChildren().add(spacer);

        if (hotkeyManager != null && hotkeyManager.isInitialized()) {
            var hotkeyRecordButton = buildHotkeyRecordButton(appPreferences, hotkeyManager);
            var resetBtn = buildResetHotkeyButton(hotkeyManager, hotkeyRecordButton);

            HBox.setMargin(resetBtn, new javafx.geometry.Insets(0, 0, 0, 8));
            mainRow.getChildren().addAll(hotkeyRecordButton, resetBtn);
        } else {
            mainRow.getChildren().add(buildLabel("Disabled"));
        }

        this.getChildren().add(mainRow);

        if (SystemUtils.isMac() && (hotkeyManager == null || !hotkeyManager.isInitialized())) {
            var verticalSpacer = new Region();
            verticalSpacer.setMinHeight(8);
            this.getChildren().addAll(verticalSpacer, buildPermissionWarning());
        }
    }

    private Label buildLabel(String text) {
        var label = new Label(text);
        label.setFont(Theme.FONT_SETTINGS);
        label.setTextFill(Theme.TEXT_PRIMARY);
        return label;
    }

    private HBox buildPermissionWarning() {
        var warningPanel = new HBox();
        warningPanel.setAlignment(Pos.CENTER_LEFT);
        warningPanel.setMaxWidth(Double.MAX_VALUE);

        var warningLabel = new Label("Grant Accessibility permissions in System Settings and restart the application");
        warningLabel.setFont(Font.font(Theme.FONT_SETTINGS.getFamily(), FontPosture.ITALIC, 11));
        warningLabel.setTextFill(Color.rgb(255, 180, 0));

        warningPanel.getChildren().add(warningLabel);

        return warningPanel;
    }

    private AnimatedSettingsButton buildHotkeyRecordButton(AppPreferences appPreferences, GlobalHotkeyManager hotkeyManager) {
        var currentHotkey = GlobalHotkeyManager.getHotkeyText(appPreferences.getHotkeyToStartApplication());
        var initialText = currentHotkey.isEmpty() ? "Click to Record" : currentHotkey;
        var btnRef = new AtomicReference<AnimatedSettingsButton>();

        Runnable action = () -> {
            var button = btnRef.get();
            if (button != null) {
                button.setText("Press keys... (Esc to cancel)");
                hotkeyManager.startRecording(() -> {
                    var newHotkey = GlobalHotkeyManager.getHotkeyText(appPreferences.getHotkeyToStartApplication());
                    javafx.application.Platform.runLater(() -> button.setText(newHotkey));
                });
            }
        };

        var hotkeyRecordBtn = new AnimatedSettingsButton(initialText, action);
        btnRef.set(hotkeyRecordBtn);
        return hotkeyRecordBtn;
    }

    private ColorfulButton buildResetHotkeyButton(GlobalHotkeyManager hotkeyManager, AnimatedSettingsButton hotkeyRecordButton) {
        var resetColor = Color.rgb(255, 94, 91);
        var buttonText = SystemUtils.isWindows() ? "X" : "✖";

        return new ColorfulButton(buttonText, resetColor, () -> {
            if (hotkeyManager != null) {
                hotkeyManager.clearHotkey();
                hotkeyRecordButton.setText("None");
            }
        });
    }
}
