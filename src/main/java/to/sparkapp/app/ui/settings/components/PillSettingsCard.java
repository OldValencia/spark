package to.sparkapp.app.ui.settings.components;

import to.sparkapp.app.config.AppPreferences;

public final class PillSettingsCard extends SettingsCard {
    public PillSettingsCard(AppPreferences preferences, Runnable onChanged) {
        var quickOpen = new SettingsToggleRow("Quick upward gesture",
                "Open Spark with a fast upward movement while the pill is hidden. Requires auto-hide.",
                preferences.isFastTopEdgeReveal(), enabled -> {
            preferences.setFastTopEdgeReveal(enabled);
            onChanged.run();
        });
        quickOpen.setDisable(preferences.isTopPill());

        var autoHide = new SettingsToggleRow("Auto-hide top-edge pill",
                "Keep the pill hidden until the pointer approaches the top-center edge", !preferences.isTopPill(),
                enabled -> {
                    preferences.setTopPill(!enabled);
                    quickOpen.getToggle().setSelected(preferences.isFastTopEdgeReveal());
                    quickOpen.setDisable(!enabled);
                    onChanged.run();
                });

        var hover = new SettingsToggleRow("Open on top-edge hover",
                "Open Spark after hovering at the top edge when the pill stays visible", preferences.isTopEdgeReveal(),
                enabled -> {
                    preferences.setTopEdgeReveal(enabled);
                    onChanged.run();
                });

        var fullscreen = new SettingsToggleRow("Hide over fullscreen apps",
                "Over games, videos and presentations, hide the pill and ignore the top edge. The hotkey still works.",
                preferences.isTopEdgeQuietInFullscreen(), enabled -> {
                    preferences.setTopEdgeQuietInFullscreen(enabled);
                    onChanged.run();
                });

        addRow(autoHide);
        addRow(hover);
        addRow(quickOpen);
        addRow(fullscreen);
    }
}
