package io.loom.app.ui.settings.components;

import io.loom.app.ui.Theme;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

public class SettingsRow extends HBox {

    public SettingsRow(String labelText, Node control) {
        this.setAlignment(Pos.CENTER_LEFT);
        this.setMaxWidth(Double.MAX_VALUE);

        var label = new Label(labelText);
        label.setFont(Theme.FONT_SETTINGS);
        label.setTextFill(Theme.TEXT_PRIMARY);
        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        this.getChildren().addAll(label, spacer, control);
    }
}
