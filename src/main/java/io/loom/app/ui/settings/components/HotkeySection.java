package io.loom.app.ui.settings.components;

import io.loom.app.config.AppPreferences;
import io.loom.app.ui.Theme;
import io.loom.app.utils.GlobalHotkeyManager;
import io.loom.app.utils.SystemUtils;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicReference;

public class HotkeySection extends JPanel {

    public HotkeySection(AppPreferences appPreferences, GlobalHotkeyManager hotkeyManager) {
        super();
        this.setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        this.setOpaque(false);
        this.setAlignmentX(Component.LEFT_ALIGNMENT);
        this.setMaximumSize(new Dimension(Short.MAX_VALUE, 40));

        var mainRow = new JPanel();
        mainRow.setLayout(new BoxLayout(mainRow, BoxLayout.X_AXIS));
        mainRow.setOpaque(false);
        mainRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        mainRow.add(buildLabel("Toggle Window Shortcut"));
        mainRow.add(Box.createHorizontalGlue());

        if (hotkeyManager != null && hotkeyManager.isInitialized()) {
            var hotkeyRecordButton = buildHotkeyRecordButton(appPreferences, hotkeyManager);
            mainRow.add(hotkeyRecordButton);
            mainRow.add(Box.createHorizontalStrut(8));
            mainRow.add(buildResetHotkeyButton(hotkeyManager, hotkeyRecordButton));
        } else {
            mainRow.add(buildLabel("Disabled"));
        }

        this.add(mainRow);

        if (SystemUtils.isMac() && (hotkeyManager == null || !hotkeyManager.isInitialized())) {
            this.add(Box.createVerticalStrut(8));
            this.add(buildPermissionWarning());
        }
    }

    private JLabel buildLabel(String text) {
        var label = new JLabel(text);
        label.setFont(Theme.FONT_SETTINGS);
        label.setForeground(Theme.TEXT_PRIMARY);
        return label;
    }

    private JPanel buildPermissionWarning() {
        var warningPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        warningPanel.setOpaque(false);
        warningPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        warningPanel.setMaximumSize(new Dimension(Short.MAX_VALUE, 30));

        var warningLabel = new JLabel("<html><i>Grant Accessibility permissions in System Settings and restart the application</i></html>");
        warningLabel.setFont(Theme.FONT_SETTINGS.deriveFont(11f));
        warningLabel.setForeground(new Color(255, 180, 0));
        warningPanel.add(warningLabel);

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
                    button.setText(newHotkey);
                });
            }
        };

        var hotkeyRecordBtn = new AnimatedSettingsButton(initialText, action);
        btnRef.set(hotkeyRecordBtn);
        return hotkeyRecordBtn;
    }

    private ColorfulButton buildResetHotkeyButton(GlobalHotkeyManager hotkeyManager, AnimatedSettingsButton hotkeyRecordButton) {
        var resetColor = new Color(255, 94, 91);
        var buttonText = SystemUtils.isWindows() ? "X" : "✖";
        return new ColorfulButton(buttonText, resetColor, () -> {
            if (hotkeyManager != null) {
                hotkeyManager.clearHotkey();
                hotkeyRecordButton.setText("None");
            }
        });
    }
}
