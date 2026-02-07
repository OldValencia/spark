package io.loom.app.ui.settings;

import io.loom.app.config.AiConfiguration;
import io.loom.app.config.AppPreferences;
import io.loom.app.ui.Theme;
import io.loom.app.ui.settings.components.AnimatedToggleSwitch;
import io.loom.app.ui.settings.components.ClearCookiesButton;
import io.loom.app.ui.settings.components.DonationSection;
import io.loom.app.ui.settings.components.GithubLinkPanel;
import io.loom.app.ui.settings.components.HotkeySection;
import io.loom.app.ui.settings.components.ProvidersManagementPanel;
import io.loom.app.ui.settings.components.ResetProvidersPanel;
import io.loom.app.ui.settings.components.SettingsRow;
import io.loom.app.ui.settings.components.SettingsSection;
import io.loom.app.utils.GlobalHotkeyManager;
import io.loom.app.utils.UpdateChecker;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

@Slf4j
public class SettingsPanel extends JPanel {

    private static final int SECTION_GAP = 20;
    private static final int ROW_GAP = 8;
    private static final int SUB_SECTION_GAP = 12;

    @Setter
    private Consumer<Boolean> onRememberLastAiChanged;
    @Setter
    private Runnable onClearCookies;
    @Setter
    private Runnable onProvidersChanged;
    @Setter
    private Consumer<Boolean> onZoomEnabledChanged;
    @Setter
    private Consumer<Boolean> onAutoUpdateChanged;

    private final AppPreferences appPreferences;
    private final AiConfiguration aiConfiguration;
    private final GlobalHotkeyManager hotkeyManager;

    public SettingsPanel(AppPreferences appPreferences, GlobalHotkeyManager hotkeyManager, AiConfiguration aiConfiguration) {
        this.appPreferences = appPreferences;
        this.hotkeyManager = hotkeyManager;
        this.aiConfiguration = aiConfiguration;

        initLayout();
        buildUI();

        if (appPreferences.isCheckUpdatesOnStartupEnabled()) {
            SwingUtilities.invokeLater(() -> UpdateChecker.check(this));
        }
    }

    private void initLayout() {
        this.setOpaque(true);
        this.setBackground(Theme.BG_BAR);
        this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        this.setAlignmentX(Component.LEFT_ALIGNMENT);
        this.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
    }

    private void buildUI() {
        this.add(new DonationSection());
        this.add(Box.createVerticalStrut(SECTION_GAP));

        buildProvidersSection();
        this.add(Box.createVerticalStrut(SECTION_GAP));

        buildGeneralSection();
        this.add(Box.createVerticalStrut(16));

        if (hotkeyManager != null) {
            buildHotkeySection();
            add(Box.createVerticalStrut(16));
        }

        buildBrowserSection();
        this.add(Box.createVerticalStrut(SUB_SECTION_GAP));

        var clearCookiesBtn = new ClearCookiesButton();
        clearCookiesBtn.setOnClearCookies(() -> {
            if (onClearCookies != null) {
                onClearCookies.run();
            }
        });
        this.add(clearCookiesBtn);
        this.add(Box.createVerticalGlue());
        this.add(Box.createVerticalStrut(SECTION_GAP));

        this.add(new GithubLinkPanel());
        this.add(Box.createVerticalStrut(5));
    }

    private void buildProvidersSection() {
        this.add(new ProvidersManagementPanel(
                aiConfiguration.getCustomProvidersManager(),
                v -> {
                    aiConfiguration.reload();
                    if (onProvidersChanged != null) {
                        onProvidersChanged.run();
                    }
                }
        ));
        this.add(new ResetProvidersPanel(aiConfiguration, onProvidersChanged));
    }

    private void buildGeneralSection() {
        addSectionHeader("General");

        addToggleRow("Remember last used AI",
                appPreferences.isRememberLastAi(),
                val -> {
                    if (onRememberLastAiChanged != null) onRememberLastAiChanged.accept(val);
                });

        addToggleRow("Run on System Startup",
                appPreferences.isAutoStartEnabled(),
                appPreferences::setAutoStartEnabled);

        addToggleRow("Run the application in the background",
                appPreferences.isStartApplicationHiddenEnabled(),
                appPreferences::setStartApplicationHiddenEnabled);

        addToggleRow("Check for Updates automatically",
                appPreferences.isCheckUpdatesOnStartupEnabled(),
                val -> {
                    appPreferences.setCheckUpdatesOnStartup(val);
                    if (onAutoUpdateChanged != null) onAutoUpdateChanged.accept(val);
                });
    }

    private void buildHotkeySection() {
        addSectionHeader("Global Hotkey");
        add(new HotkeySection(appPreferences, hotkeyManager));
    }

    private void buildBrowserSection() {
        addSectionHeader("Browser");

        addToggleRow("Zoom enabled",
                appPreferences.isZoomEnabled(),
                val -> {
                    if (onZoomEnabledChanged != null) onZoomEnabledChanged.accept(val);
                });

        addToggleRow("Try to request dark mode from websites (restart required)",
                appPreferences.isDarkModeEnabled(),
                appPreferences::setDarkModeEnabled);
    }

    private void addSectionHeader(String title) {
        this.add(new SettingsSection(title));
        this.add(Box.createVerticalStrut(10));
    }

    private void addToggleRow(String labelText, boolean initialValue, Consumer<Boolean> onChange) {
        var toggle = new AnimatedToggleSwitch(initialValue);
        toggle.setOnChange(onChange);

        addSettingRow(labelText, toggle);
    }

    private void addSettingRow(String labelText, JComponent control) {
        this.add(new SettingsRow(labelText, control));
        this.add(Box.createVerticalStrut(ROW_GAP));
    }
}
