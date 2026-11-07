package to.sparkapp.app.ui.settings.components;

import java.util.function.Consumer;

import to.sparkapp.app.config.AppPreferences;

public final class GeneralSettingsCard extends SettingsCard {
    public GeneralSettingsCard(AppPreferences preferences, Consumer<Boolean> rememberChanged, Consumer<Boolean> updatesChanged, Runnable appearanceChanged) {
        addRow(new SettingsToggleRow("Remember last site", "Reopen on the page you left",
                preferences.isRememberLastAi(), rememberChanged));

        addRow(new SettingsToggleRow("Run on system startup", "Launch Spark when you sign in",
                preferences.isAutoStartEnabled(), preferences::setAutoStartEnabled));

        addRow(new SettingsToggleRow("Start in the background", "Stay in the tray until the hotkey is pressed",
                preferences.isStartApplicationHiddenEnabled(), preferences::setStartApplicationHiddenEnabled));

        addRow(new SettingsToggleRow("Check for updates automatically", "Look for a new release on startup",
                preferences.isCheckUpdatesOnStartupEnabled(), enabled -> {
            preferences.setCheckUpdatesOnStartup(enabled);
            updatesChanged.accept(enabled);
        }));

        addRow(new SettingsToggleRow("Show mascot", "A small companion with gentle, local reactions",
                preferences.isMascotEnabled(), enabled -> {
            preferences.setMascotEnabled(enabled);
            appearanceChanged.run();
        }));

        addRow(new SettingsToggleRow("Reduce motion", "Keep the mascot still and reduce interface animations",
                preferences.isReducedMotion(), enabled -> {
            preferences.setReducedMotion(enabled);
            appearanceChanged.run();
        }));
    }
}
