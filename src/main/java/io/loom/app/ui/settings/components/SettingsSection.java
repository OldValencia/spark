package io.loom.app.ui.settings.components;

import io.loom.app.ui.Theme;
import javafx.scene.control.Label;

public class SettingsSection extends Label {

    public SettingsSection(String title) {
        super(title.toUpperCase());
        this.setFont(Theme.FONT_SETTINGS_SECTION);
        this.setTextFill(Theme.TEXT_TERTIARY);
    }
}
