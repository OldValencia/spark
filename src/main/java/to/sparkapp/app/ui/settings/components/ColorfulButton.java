package to.sparkapp.app.ui.settings.components;

import to.sparkapp.app.ui.Theme;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.util.Duration;

public class ColorfulButton extends Button {

    private final Color normalBg;
    private final Color hoverBg;
    private final Color normalBorder;
    private final Color hoverBorder;
    private final String textFill;
    private final String padding;
    private final String fontSize;

    private final SimpleDoubleProperty hoverT = new SimpleDoubleProperty(0.0);
    private final Timeline hoverAnim = new Timeline();

    public ColorfulButton(String text, Color accentColor, Runnable action) {
        super(text);
        this.setCursor(Cursor.HAND);
        var isIconMode = text.length() <= 2;

        normalBg = Theme.BG_POPUP;
        var mixedBg = Color.color(
                Math.min(1.0, (Theme.BG_POPUP.getRed()   + accentColor.getRed())   / 2.0),
                Math.min(1.0, (Theme.BG_POPUP.getGreen() + accentColor.getGreen()) / 2.0),
                Math.min(1.0, (Theme.BG_POPUP.getBlue()  + accentColor.getBlue())  / 2.0)
        );
        hoverBg = Theme.lerp(Theme.BG_POPUP, mixedBg, 0.25);
        normalBorder = Theme.lerp(Theme.BG_POPUP, accentColor, 0.55);
        hoverBorder  = accentColor;
        textFill     = Theme.toHex(Theme.TEXT_PRIMARY);

        padding  = isIconMode ? "0"          : "0 20 0 20";
        fontSize = isIconMode ? "16px"       : "14px";

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

        hoverT.addListener((obs, old, val) -> applyStyle(val.doubleValue()));
        applyStyle(0.0);

        this.setOnMouseEntered(e -> animateTo(1.0));
        this.setOnMouseExited(e  -> animateTo(0.0));

        this.setOnAction(e -> {
            e.consume();
            if (action != null) action.run();
        });
    }

    private void animateTo(double target) {
        hoverAnim.stop();
        hoverAnim.getKeyFrames().clear();
        hoverAnim.getKeyFrames().add(new KeyFrame(Duration.millis(160),
                new KeyValue(hoverT, target, Interpolator.EASE_OUT)
        ));
        hoverAnim.play();
    }

    private void applyStyle(double t) {
        Color bg     = Theme.lerp(normalBg,     hoverBg,     t);
        Color border = Theme.lerp(normalBorder, hoverBorder, t);

        this.setStyle(String.format(
                "-fx-background-color: %s; " +
                        "-fx-border-color: %s; " +
                        "-fx-border-width: 1px; " +
                        "-fx-text-fill: %s; " +
                        "-fx-background-radius: 10px; " +
                        "-fx-border-radius: 10px; " +
                        "-fx-font-size: %s; " +
                        "-fx-padding: %s;",
                Theme.toHex(bg),
                Theme.toHex(border),
                textFill,
                fontSize,
                padding
        ));
    }
}
