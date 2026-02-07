package io.loom.app.ui.settings.components;

import io.loom.app.ui.Theme;
import io.loom.app.utils.GlobalHotkeyManager;
import io.loom.app.utils.SystemUtils;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class HotkeySection extends JPanel {
    public HotkeySection(List<Integer> hotkeyToStartApplication, GlobalHotkeyManager hotkeyManager) {
        super();
        this.setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        this.setOpaque(false);
        this.setAlignmentX(Component.LEFT_ALIGNMENT);
        this.setMaximumSize(new Dimension(Short.MAX_VALUE, 40));

        // Main row
        var mainRow = new JPanel();
        mainRow.setLayout(new BoxLayout(mainRow, BoxLayout.X_AXIS));
        mainRow.setOpaque(false);
        mainRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        mainRow.add(buildLabel("Toggle Window Shortcut"));
        mainRow.add(Box.createHorizontalGlue());

        if (hotkeyManager != null && hotkeyManager.isInitialized()) {
            var hotkeyRecordButton = buildHotkeyRecordButton(hotkeyToStartApplication, hotkeyManager);
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

        var warningLabel = new JLabel("<html><i>⚠️ Accessibility permissions required in System Settings</i></html>");
        warningLabel.setFont(Theme.FONT_SETTINGS.deriveFont(11f));
        warningLabel.setForeground(new Color(255, 180, 0));
        warningPanel.add(warningLabel);

        return warningPanel;
    }

    private AnimatedSettingsButton buildHotkeyRecordButton(List<Integer> hotkeyToStartApplication, GlobalHotkeyManager hotkeyManager) {
        var currentHotkey = GlobalHotkeyManager.getHotkeyText(hotkeyToStartApplication);
        var initialText = currentHotkey.isEmpty() ? "Click to Record" : currentHotkey;
        var btnRef = new AtomicReference<AnimatedSettingsButton>();

        Runnable action = () -> {
            var button = btnRef.get();
            if (button != null) {
                button.setText("Press keys... (Esc to cancel)");
                hotkeyManager.startRecording(() -> {
                    var newHotkey = GlobalHotkeyManager.getHotkeyText(hotkeyToStartApplication);
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
        return new ColorfulButton("✖", resetColor, () -> {
            if (hotkeyManager != null) {
                hotkeyManager.clearHotkey();
                hotkeyRecordButton.setText("None");
            }
        });
    }
}
