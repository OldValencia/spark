package io.loom.app.ui.settings.components;

import io.loom.app.ui.Theme;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

class ProvidersListTextButton extends Label {

    ProvidersListTextButton(String text, Color defaultColor, Runnable action) {
        super(text);
        this.setFont(Font.font(Theme.FONT_SETTINGS.getFamily(), 12));
        this.setTextFill(defaultColor);
        this.setCursor(Cursor.HAND);

        this.setOnMouseClicked(e -> {
            if (action != null) {
                action.run();
            }
        });

        this.setOnMouseEntered(e -> {
            if (defaultColor.equals(Theme.TEXT_SECONDARY)) {
                this.setTextFill(Theme.ACCENT);
            }
        });

        this.setOnMouseExited(e -> this.setTextFill(defaultColor));
    }
}
