package io.loom.app.ui.settings;

import io.loom.app.config.AppPreferences;
import io.loom.app.ui.Theme;
import io.loom.app.ui.settings.components.AnimatedSettingsButton;
import io.loom.app.ui.settings.components.AnimatedToggleSwitch;
import io.loom.app.ui.settings.components.ColorfulButton;
import io.loom.app.utils.GlobalHotkeyManager;
import io.loom.app.utils.UpdateChecker;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.util.function.Consumer;

@Slf4j
public class SettingsPanel extends JPanel {

    @Setter
    private Consumer<Boolean> onRememberLastAiChanged;
    @Setter
    private Runnable onClearCookies;
    @Setter
    private Consumer<Boolean> onZoomEnabledChanged;
    @Setter
    private Consumer<Boolean> onAutoUpdateChanged;

    private final AppPreferences appPreferences;
    private final GlobalHotkeyManager hotkeyManager;
    private AnimatedSettingsButton hotkeyRecordBtn;

    public SettingsPanel(AppPreferences appPreferences, GlobalHotkeyManager hotkeyManager) {
        this.appPreferences = appPreferences;
        this.hotkeyManager = hotkeyManager;

        setOpaque(true);
        setBackground(Theme.BG_BAR);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setAlignmentX(Component.LEFT_ALIGNMENT);
        setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        addDonationSection();
        add(Box.createVerticalStrut(20));

        buildSection("General", appPreferences.isRememberLastAi(), onRememberLastAiChanged, "Remember last used AI");

        var autoStartToggle = new AnimatedToggleSwitch(appPreferences.isAutoStartEnabled());
        autoStartToggle.setOnChange(appPreferences::setAutoStartEnabled);
        addSettingRow("Run on System Startup", autoStartToggle);

        var updateToggle = new AnimatedToggleSwitch(appPreferences.isCheckUpdatesOnStartupEnabled());
        updateToggle.setOnChange(val -> {
            appPreferences.setCheckUpdatesOnStartup(val);
            if (onAutoUpdateChanged != null) onAutoUpdateChanged.accept(val);
        });
        addSettingRow("Check for Updates automatically", updateToggle);

        add(Box.createVerticalStrut(16));

        if (hotkeyManager != null) {
            addSection("Global Hotkey");
            addHotkeySection();
            add(Box.createVerticalStrut(16));
        }

        buildSection("Browser", appPreferences.isZoomEnabled(), onZoomEnabledChanged, "Zoom enabled");
        add(Box.createVerticalStrut(12));

        var buttonsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        buttonsRow.setOpaque(false);
        buttonsRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttonsRow.setMaximumSize(new Dimension(Short.MAX_VALUE, 40));

        var clearCookiesBtn = new AnimatedSettingsButton("Clear cookies", () -> {
            if (onClearCookies != null) onClearCookies.run();
        });
        buttonsRow.add(clearCookiesBtn);
        add(buttonsRow);

        add(Box.createVerticalGlue());
        add(Box.createVerticalStrut(20));
        addGithubLink();
        add(Box.createVerticalStrut(5));

        if (appPreferences.isCheckUpdatesOnStartupEnabled()) {
            UpdateChecker.check(this);
        }
    }

    private void addGithubLink() {
        var footerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        footerPanel.setOpaque(false);
        footerPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        footerPanel.setMaximumSize(new Dimension(Short.MAX_VALUE, 30));

        var label = new JLabel("Loom application on Github");
        label.setFont(Theme.FONT_SETTINGS.deriveFont(11f));
        label.setForeground(Theme.TEXT_TERTIARY);
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                openLink("https://github.com/oldvalencia/loom");
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                label.setForeground(Theme.TEXT_SECONDARY);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                label.setForeground(Theme.TEXT_TERTIARY);
            }
        });

        footerPanel.add(label);
        add(footerPanel);
    }

    private void addHotkeySection() {
        var row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Short.MAX_VALUE, 40));

        var label = new JLabel("Toggle Window Shortcut");
        label.setFont(Theme.FONT_SETTINGS);
        label.setForeground(Theme.TEXT_PRIMARY);

        var currentHotkey = GlobalHotkeyManager.getHotkeyText(appPreferences.getHotkeyToStartApplication());

        hotkeyRecordBtn = new AnimatedSettingsButton(currentHotkey.isEmpty() ? "Click to Record" : currentHotkey, () -> {
            if (hotkeyManager != null) {
                hotkeyRecordBtn.setText("Press keys... (Esc to cancel)");
                hotkeyManager.startRecording(() -> {
                    var newHotkey = GlobalHotkeyManager.getHotkeyText(appPreferences.getHotkeyToStartApplication());
                    hotkeyRecordBtn.setText(newHotkey);
                });
            }
        });

        var resetColor = new Color(255, 94, 91);
        var resetBtn = new ColorfulButton("✖", resetColor, () -> {
            if (hotkeyManager != null) {
                hotkeyManager.clearHotkey();
                hotkeyRecordBtn.setText("None");
            }
        });

        row.add(label);
        row.add(Box.createHorizontalGlue());
        row.add(hotkeyRecordBtn);
        row.add(Box.createHorizontalStrut(8));
        row.add(resetBtn);
        add(row);
    }

    private void addDonationSection() {
        var buttonsRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        buttonsRow.setOpaque(false);
        buttonsRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttonsRow.setMaximumSize(new Dimension(Short.MAX_VALUE, 40));

        var coffeeColor = new Color(255, 200, 0);
        var kofiColor = new Color(255, 94, 91);

        buttonsRow.add(new ColorfulButton("☕ Buy me a coffee", coffeeColor,
                () -> openLink("https://buymeacoffee.com/oldvalencia")));

        buttonsRow.add(new ColorfulButton("❤️ Ko-Fi", kofiColor,
                () -> openLink("https://ko-fi.com/oldvalencia")));

        add(buttonsRow);
    }

    private void openLink(String url) {
        try {
            Desktop.getDesktop().browse(new URI(url));
        } catch (Exception e) {
            log.error("Error opening link", e);
        }
    }

    private void buildSection(String title, boolean toggleVal, Consumer<Boolean> onChanged, String rowString) {
        addSection(title);
        var toggle = new AnimatedToggleSwitch(toggleVal);
        toggle.setOnChange(val -> {
            if (onChanged != null) onChanged.accept(val);
        });
        addSettingRow(rowString, toggle);
    }

    private void addSection(String title) {
        var label = new JLabel(title.toUpperCase());
        label.setFont(Theme.FONT_SETTINGS_SECTION);
        label.setForeground(Theme.TEXT_TERTIARY);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(label);
        add(Box.createVerticalStrut(10));
    }

    private void addSettingRow(String labelText, JComponent control) {
        var row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Short.MAX_VALUE, 30));

        var label = new JLabel(labelText);
        label.setFont(Theme.FONT_SETTINGS);
        label.setForeground(Theme.TEXT_PRIMARY);

        row.add(label);
        row.add(Box.createHorizontalGlue());
        row.add(control);
        add(row);
        add(Box.createVerticalStrut(8));
    }
}
