package to.sparkapp.app.ui.settings;

import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.ui.Theme;
import to.sparkapp.app.ui.settings.components.AnimatedSettingsButton;
import to.sparkapp.app.ui.settings.components.DonationSection;
import to.sparkapp.app.ui.settings.components.GeneralSettingsCard;
import to.sparkapp.app.ui.settings.components.HotkeySection;
import to.sparkapp.app.ui.settings.components.PillSettingsCard;
import to.sparkapp.app.ui.settings.components.SettingsCard;
import to.sparkapp.app.ui.settings.components.SettingsRow;
import to.sparkapp.app.ui.settings.components.SettingsSection;
import to.sparkapp.app.ui.settings.components.SettingsToggleRow;
import to.sparkapp.app.utils.GlobalHotkeyManager;
import to.sparkapp.app.utils.UpdateChecker;

@Slf4j
public class SettingsPanel extends VBox {

    private static final int SECTION_GAP = 22;
    private static final int HEADER_GAP = 8;

    @Setter
    private Consumer<Boolean> onRememberLastAiChanged;
    @Setter
    private Runnable onClearCookies;
    @Setter
    private Consumer<Boolean> onAutoUpdateChanged;
    @Setter
    private Consumer<Boolean> onZoomEnabledChanged;

    @Setter
    private Runnable onBackgroundMemoryChanged;
    @Setter
    private Runnable onPillSettingsChanged;
    @Setter
    private Runnable onAppearanceChanged;

    private final AppPreferences appPreferences;
    private final GlobalHotkeyManager hotkeyManager;

    public SettingsPanel(AppPreferences appPreferences, GlobalHotkeyManager hotkeyManager) {
        this.appPreferences = appPreferences;
        this.hotkeyManager = hotkeyManager;

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
        this.setPadding(new Insets(14, 20, 18, 20));
        this.setMaxWidth(Double.MAX_VALUE);
        this.setFillWidth(true);
    }

    private void buildUI() {
        this.getChildren().add(new DonationSection());
        addStrut(SECTION_GAP);

        if (hotkeyManager != null) {
            buildHotkeySection();
            addStrut(SECTION_GAP);
        }

        buildGeneralSection();
        addStrut(SECTION_GAP);

        addSection("Pill", new PillSettingsCard(appPreferences, this::pillSettingsChanged));
        addStrut(SECTION_GAP);

        buildBrowserSection();
        addStrut(18);
    }

    private void buildGeneralSection() {
        addSection("General", new GeneralSettingsCard(appPreferences, enabled -> {
            if (onRememberLastAiChanged != null) {
                onRememberLastAiChanged.accept(enabled);
            }
        }, enabled -> {
            if (onAutoUpdateChanged != null) {
                onAutoUpdateChanged.accept(enabled);
            }
        }, this::appearanceChanged));
    }

    private void appearanceChanged() {
        if (onAppearanceChanged != null) {
            onAppearanceChanged.run();
        }
    }

    private void pillSettingsChanged() {
        if (onPillSettingsChanged != null) {
            onPillSettingsChanged.run();
        }
    }

    private void buildHotkeySection() {
        var card = new SettingsCard();
        card.addRow(new HotkeySection(appPreferences, hotkeyManager));
        addSection("Global Hotkey", card);
    }

    private void buildBrowserSection() {
        var card = new SettingsCard();

        card.addRow(
                toggleRow("Zoom", "Ctrl + wheel or Ctrl +/− inside the page", appPreferences.isZoomEnabled(), val -> {
                    appPreferences.setZoomEnabled(val);

                    if (onZoomEnabledChanged != null) {
                        onZoomEnabledChanged.accept(val);
                    }
                }));

        card.addRow(toggleRow("Request dark mode from websites", "Takes effect after a restart",
                appPreferences.isDarkModeEnabled(), appPreferences::setDarkModeEnabled));

        card.addRow(
                toggleRow("Reduce background memory", "Keep hidden pages alive while allowing Windows to reclaim RAM",
                        appPreferences.isBackgroundMemoryReductionEnabled(), enabled -> {
                            appPreferences.setBackgroundMemoryReductionEnabled(enabled);

                            if (onBackgroundMemoryChanged != null) {
                                onBackgroundMemoryChanged.run();
                            }
                        }));

        var clearCookiesBtn = new AnimatedSettingsButton("Clear site data", () -> {
            if (onClearCookies != null) {
                onClearCookies.run();
            }
        });
        card.addRow(new SettingsRow("Site data", "Clear cookies and website storage for all sites", clearCookiesBtn));

        addSection("Browser", card);
    }

    private void addSection(String title, Node card) {
        this.getChildren().add(new SettingsSection(title));
        addStrut(HEADER_GAP);
        this.getChildren().add(card);
    }

    private SettingsRow toggleRow(String title, String description, boolean initialValue, Consumer<Boolean> onChange) {
        return new SettingsToggleRow(title, description, initialValue, onChange);
    }

    private void addStrut(double height) {
        var spacer = new Region();
        spacer.setMinHeight(height);
        spacer.setPrefHeight(height);
        spacer.setMaxHeight(height);
        this.getChildren().add(spacer);
    }
}
