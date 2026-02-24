package to.sparkapp.app.ui.settings;

import to.sparkapp.app.config.AiConfiguration;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.ui.Theme;
import to.sparkapp.app.ui.settings.components.*;
import to.sparkapp.app.utils.GlobalHotkeyManager;
import to.sparkapp.app.utils.UpdateChecker;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;

@Slf4j
public class SettingsPanel extends VBox {

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
            Platform.runLater(() -> {
                var scene = this.getScene();
                if (scene != null) {
                    UpdateChecker.check(scene.getWindow());
                } else {
                    UpdateChecker.check(null);
                }
            });
        }
    }

    private void initLayout() {
        this.setStyle("-fx-background-color: " + Theme.toHex(Theme.BG_BAR) + ";");
        this.setPadding(new Insets(20, 24, 20, 24));
        this.setMaxWidth(820);
    }

    private void buildUI() {
        this.getChildren().add(new DonationSection());
        addStrut(SECTION_GAP);

        buildProvidersSection();
        addStrut(SECTION_GAP);

        buildGeneralSection();
        addStrut(16);

        if (hotkeyManager != null) {
            buildHotkeySection();
            addStrut(16);
        }

        buildBrowserSection();
        addStrut(SUB_SECTION_GAP);

        var clearCookiesBtn = new ClearCookiesButton();
        clearCookiesBtn.setOnClearCookies(() -> {
            if (onClearCookies != null) {
                onClearCookies.run();
            }
        });
        this.getChildren().add(clearCookiesBtn);

        addStrut(SECTION_GAP);

        this.getChildren().add(new GithubLinkPanel());
        addStrut(5);
    }

    private void buildProvidersSection() {
        this.getChildren().add(new ProvidersManagementPanel(
                aiConfiguration.getCustomProvidersManager(),
                v -> {
                    aiConfiguration.reload();
                    if (onProvidersChanged != null) {
                        onProvidersChanged.run();
                    }
                }
        ));
        this.getChildren().add(new ResetProvidersPanel(aiConfiguration, onProvidersChanged));
    }

    private void buildGeneralSection() {
        addSectionHeader("General");

        addToggleRow("Remember last used AI",
                appPreferences.isRememberLastAi(),
                val -> {
                    if (onRememberLastAiChanged != null) {
                        onRememberLastAiChanged.accept(val);
                    }
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
                    if (onAutoUpdateChanged != null) {
                        onAutoUpdateChanged.accept(val);
                    }
                });
    }

    private void buildHotkeySection() {
        addSectionHeader("Global Hotkey");
        this.getChildren().add(new HotkeySection(appPreferences, hotkeyManager));
    }

    private void buildBrowserSection() {
        addSectionHeader("Browser");

        addToggleRow("Zoom enabled",
                appPreferences.isZoomEnabled(),
                val -> {
                    if (onZoomEnabledChanged != null) {
                        onZoomEnabledChanged.accept(val);
                    }
                });

        addToggleRow("Try to request dark mode from websites (restart required)",
                appPreferences.isDarkModeEnabled(),
                appPreferences::setDarkModeEnabled);
    }

    private void addSectionHeader(String title) {
        this.getChildren().add(new SettingsSection(title));
        addStrut(10);
    }

    private void addToggleRow(String labelText, boolean initialValue, Consumer<Boolean> onChange) {
        var toggle = new AnimatedToggleSwitch(initialValue);
        toggle.setOnChange(onChange);

        addSettingRow(labelText, toggle);
    }

    private void addSettingRow(String labelText, Node control) {
        this.getChildren().add(new SettingsRow(labelText, control));
        addStrut(ROW_GAP);
    }

    private void addStrut(double height) {
        var spacer = new Region();
        spacer.setMinHeight(height);
        spacer.setPrefHeight(height);
        spacer.setMaxHeight(height);
        this.getChildren().add(spacer);
    }
}
