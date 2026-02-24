package io.loom.app.ui.settings.components;

import io.loom.app.ui.Theme;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.text.Font;

class ProvidersEmptyListLabel extends Label {
    ProvidersEmptyListLabel() {
        super("No providers available. Click '+ Add' to create a custom one.");
        this.setFont(Font.font(Theme.FONT_SETTINGS.getFamily(), 12));
        this.setTextFill(Theme.TEXT_TERTIARY);
        this.setPadding(new Insets(20, 0, 20, 0));
    }
}
