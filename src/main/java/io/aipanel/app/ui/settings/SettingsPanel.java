package io.aipanel.app.ui.settings;

import io.aipanel.app.config.AppPreferences;
import io.aipanel.app.ui.Theme;
import io.aipanel.app.ui.settings.components.AnimatedSettingsButton;
import lombok.Setter;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

public class SettingsPanel extends JPanel {

    @Setter
    private Consumer<Boolean> onRememberLastAiChanged;
    @Setter
    private Runnable onClearCookies;
    @Setter
    private Consumer<Boolean> onZoomEnabledChanged;

    private final JCheckBox rememberCheckBox;
    private final JCheckBox zoomCheckBox;

    public SettingsPanel(AppPreferences appPreferences) {
        setOpaque(true);
        setBackground(Theme.BG_BAR);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));

        rememberCheckBox = createCheckBox("Remember last opened AI");
        rememberCheckBox.setSelected(appPreferences.isRememberLastAi());
        zoomCheckBox = createCheckBox("Zoom enabled");
        zoomCheckBox.setSelected(appPreferences.isZoomEnabled());
        var clearCookiesButton = new AnimatedSettingsButton("Clear cookies", () -> {
            if (onClearCookies != null) onClearCookies.run();
        });

        rememberCheckBox.addItemListener(e -> {
            if (onRememberLastAiChanged != null)
                onRememberLastAiChanged.accept(rememberCheckBox.isSelected());
        });
        zoomCheckBox.addItemListener(e -> {
            if (onZoomEnabledChanged != null)
                onZoomEnabledChanged.accept(zoomCheckBox.isSelected());
        });

        add(rememberCheckBox);
        add(Box.createVerticalStrut(6));
        add(zoomCheckBox);
        add(Box.createVerticalStrut(6));
        add(clearCookiesButton);
    }

    private static JCheckBox createCheckBox(String text) {
        var checkBox = new JCheckBox(text);
        checkBox.setOpaque(false);
        checkBox.setFont(Theme.FONT_SELECTOR);
        checkBox.setForeground(Theme.TEXT_PRIMARY);
        checkBox.setFocusPainted(false);
        checkBox.setBorder(BorderFactory.createEmptyBorder());
        checkBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        return checkBox;
    }
}