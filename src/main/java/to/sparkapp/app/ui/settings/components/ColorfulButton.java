package to.sparkapp.app.ui.settings.components;

import to.sparkapp.app.ui.Theme;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

public class ColorfulButton extends Button {

    public ColorfulButton(String text, Color accentColor, Runnable action) {
        super(text);
        this.setCursor(Cursor.HAND);

        var isIconMode = text.length() <= 2;

        var baseBg = Theme.toHex(Theme.BG_POPUP);
        var mixedBgColor = Color.color(
                (Theme.BG_POPUP.getRed() + accentColor.getRed()) / 2.0,
                (Theme.BG_POPUP.getGreen() + accentColor.getGreen()) / 2.0,
                (Theme.BG_POPUP.getBlue() + accentColor.getBlue()) / 2.0
        );
        var hoverBg = Theme.toHex(Theme.lerp(Theme.BG_POPUP, mixedBgColor, 0.2));

        var normalStroke = Theme.toHex(Theme.withAlpha(accentColor, 180.0 / 255.0));
        var hoverStroke = Theme.toHex(Theme.withAlpha(accentColor, 1.0));
        var textFill = Theme.toHex(Theme.TEXT_PRIMARY);

        var fontAndPadding = isIconMode ?
                "-fx-font-size: 16px; -fx-padding: 0;" :
                "-fx-font-size: 13px; -fx-padding: 0 20 0 20;";

        // ВАЖНО: Border-width всегда 1px. Меняется только цвет. Никаких дерганий!
        var normalStyle = String.format(
                "-fx-background-color: %s; -fx-border-color: %s; -fx-border-width: 1px; " +
                        "-fx-text-fill: %s; -fx-background-radius: 10px; -fx-border-radius: 10px; %s",
                baseBg, normalStroke, textFill, fontAndPadding
        );

        var hoverStyle = String.format(
                "-fx-background-color: %s; -fx-border-color: %s; -fx-border-width: 1px; " +
                        "-fx-text-fill: %s; -fx-background-radius: 10px; -fx-border-radius: 10px; %s",
                hoverBg, hoverStroke, textFill, fontAndPadding
        );

        this.setStyle(normalStyle);

        if (isIconMode) {
            this.setPrefSize(30, 30);
            this.setMinSize(30, 30);
            this.setMaxSize(30, 30);
        } else {
            this.setPrefHeight(34);
            this.setMinHeight(34);
            this.setMaxHeight(34);
        }

        this.setMaxWidth(Region.USE_PREF_SIZE);

        this.setOnMouseEntered(e -> this.setStyle(hoverStyle));
        this.setOnMouseExited(e -> this.setStyle(normalStyle));

        this.setOnAction(e -> {
            e.consume();
            if (action != null) action.run();
        });
    }
}
