package io.loom.app.ui.settings.components;

import io.loom.app.ui.Theme;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.layout.Region;

public class AnimatedSettingsButton extends Button {

    public AnimatedSettingsButton(String text, Runnable action) {
        super(text);
        this.setCursor(Cursor.HAND);

        this.setPrefHeight(30);
        this.setMinHeight(30);
        this.setMaxHeight(30);
        this.setPadding(new Insets(0, 14, 0, 14));

        this.setMaxWidth(Region.USE_PREF_SIZE);

        var normalStyle = String.format(
                "-fx-background-color: %s; -fx-border-color: %s; -fx-border-width: 1px; " +
                        "-fx-text-fill: %s; -fx-background-radius: 8px; -fx-border-radius: 8px; -fx-font-family: '%s';",
                Theme.toHex(Theme.BG_POPUP), Theme.toHex(Theme.BORDER), Theme.toHex(Theme.TEXT_PRIMARY), Theme.FONT_SELECTOR.getFamily()
        );

        var hoverStyle = String.format(
                "-fx-background-color: %s; -fx-border-color: %s; -fx-border-width: 1px; " +
                        "-fx-text-fill: %s; -fx-background-radius: 8px; -fx-border-radius: 8px; -fx-font-family: '%s';",
                Theme.toHex(Theme.BG_HOVER), Theme.toHex(Theme.BORDER), Theme.toHex(Theme.TEXT_PRIMARY), Theme.FONT_SELECTOR.getFamily()
        );

        this.setStyle(normalStyle);

        this.setOnMouseEntered(e -> this.setStyle(hoverStyle));
        this.setOnMouseExited(e -> this.setStyle(normalStyle));

        this.setOnAction(e -> {
            e.consume();
            if (action != null) {
                action.run();
            }
        });
    }
}
