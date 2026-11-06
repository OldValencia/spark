package to.sparkapp.app.ui.settings.components;

import java.util.concurrent.atomic.AtomicReference;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.ui.Theme;
import to.sparkapp.app.ui.topbar.components.ShellIcon;
import to.sparkapp.app.ui.topbar.components.ShellTooltip;
import to.sparkapp.app.utils.GlobalHotkeyManager;
import to.sparkapp.app.utils.SystemUtils;

/**
 * Row that records the global show/hide shortcut.
 */
public class HotkeySection extends VBox {

    public HotkeySection(AppPreferences appPreferences, GlobalHotkeyManager hotkeyManager) {
        setMaxWidth(Double.MAX_VALUE);
        setPadding(new Insets(11, 12, 11, 12));
        setMinHeight(56);
        setAlignment(Pos.CENTER_LEFT);

        var mainRow = new HBox(16);
        mainRow.setAlignment(Pos.CENTER_LEFT);
        mainRow.setMaxWidth(Double.MAX_VALUE);

        var available = hotkeyManager != null && hotkeyManager.isInitialized();
        mainRow.getChildren().add(buildTextBlock(available));

        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        mainRow.getChildren().add(spacer);

        if (available) {
            var hotkeyRecordButton = buildHotkeyRecordButton(appPreferences, hotkeyManager);
            var resetBtn = buildResetHotkeyButton(hotkeyManager, hotkeyRecordButton);

            var controls = new HBox(8, hotkeyRecordButton, resetBtn);
            controls.setAlignment(Pos.CENTER_RIGHT);
            mainRow.getChildren().add(controls);
        }

        this.getChildren().add(mainRow);

        if (SystemUtils.isMac() && !available) {
            var verticalSpacer = new Region();
            verticalSpacer.setMinHeight(8);
            this.getChildren().addAll(verticalSpacer, buildPermissionWarning());
        }

        SettingsRow.applyHoverHighlight(this);
    }

    private Node buildTextBlock(boolean available) {
        var title = new Label("Toggle window shortcut");
        title.getStyleClass().add("setting-title");
        title.setFont(Theme.FONT_SETTINGS_TITLE);
        title.setTextFill(Theme.TEXT_PRIMARY);

        var description = new Label(
                available ? "Click the shortcut and press a new key combination" : "Unavailable — the global key hook could not be registered");
        description.setFont(Theme.FONT_SETTINGS_HINT);
        description.getStyleClass().add("setting-description");
        description.setWrapText(true);
        description.setTextFill(Theme.TEXT_TERTIARY);

        var textBlock = new VBox(3, title, description);
        textBlock.setMinWidth(0);
        HBox.setHgrow(textBlock, Priority.ALWAYS);
        textBlock.setAlignment(Pos.CENTER_LEFT);

        return textBlock;
    }

    private HBox buildPermissionWarning() {
        var warningPanel = new HBox();
        warningPanel.setAlignment(Pos.CENTER_LEFT);
        warningPanel.setMaxWidth(Double.MAX_VALUE);

        var warningLabel = new Label(
                "Grant Accessibility permissions in System Settings and restart the" + " application");
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
                    Platform.runLater(() -> button.setText(newHotkey));
                });
            }
        };

        var hotkeyRecordBtn = new AnimatedSettingsButton(initialText, action);
        btnRef.set(hotkeyRecordBtn);

        return hotkeyRecordBtn;
    }

    private Button buildResetHotkeyButton(GlobalHotkeyManager hotkeyManager, AnimatedSettingsButton hotkeyRecordButton) {
        var button = new Button("", ShellIcon.create("x-mark"));
        button.getStyleClass().add("shell-button");
        button.setTooltip(ShellTooltip.create("Clear shortcut"));
        button.setAccessibleText("Clear shortcut");
        button.setOnAction(e -> {
            if (hotkeyManager != null) {
                hotkeyManager.clearHotkey();
                hotkeyRecordButton.setText("None");
            }
        });

        return button;
    }
}
