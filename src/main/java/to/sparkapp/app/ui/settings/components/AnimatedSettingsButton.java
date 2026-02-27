package to.sparkapp.app.ui.settings.components;

import to.sparkapp.app.ui.Theme;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.layout.Region;
import javafx.util.Duration;

public class AnimatedSettingsButton extends Button {

    private final String fontCss;
    private final String normalStyle;
    private final String hoverStyle;

    private final Timeline hoverTimeline = new Timeline();

    public AnimatedSettingsButton(String text, Runnable action) {
        super(text);
        this.setCursor(Cursor.HAND);

        this.setPrefHeight(30);
        this.setMinHeight(30);
        this.setMaxHeight(30);
        this.setPadding(new Insets(0, 14, 0, 14));
        this.setMaxWidth(Region.USE_PREF_SIZE);

        fontCss = String.format("-fx-font-family: '%s'; -fx-font-size: 13px;",
                Theme.FONT_SETTINGS.getFamily());

        normalStyle = buildStyle(Theme.toHex(Theme.BG_POPUP));
        hoverStyle  = buildStyle(Theme.toHex(Theme.BG_HOVER));

        this.setStyle(normalStyle);

        this.setOnMouseEntered(e -> {
            hoverTimeline.stop();
            hoverTimeline.getKeyFrames().clear();
            this.setStyle(hoverStyle);
        });
        this.setOnMouseExited(e -> {
            hoverTimeline.stop();
            hoverTimeline.getKeyFrames().clear();
            this.setStyle(normalStyle);
        });

        this.setOnAction(e -> {
            e.consume();
            if (action != null) action.run();
        });
    }

    private String buildStyle(String bg) {
        return String.format(
                "-fx-background-color: %s; -fx-border-color: %s; -fx-border-width: 1px; " +
                        "-fx-text-fill: %s; -fx-background-radius: 8px; -fx-border-radius: 8px; %s",
                bg, Theme.toHex(Theme.BORDER), Theme.toHex(Theme.TEXT_PRIMARY), fontCss
        );
    }
}
