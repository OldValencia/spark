package io.loom.app.ui.settings.components;

import io.loom.app.ui.Theme;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.Cursor;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import lombok.Setter;

import java.util.function.Consumer;

public class AnimatedToggleSwitch extends Pane {

    private static final double WIDTH = 48;
    private static final double HEIGHT = 28;
    private static final double THUMB_RADIUS = 11;

    private boolean enabled;
    private final Timeline timeline;

    @Setter
    private Consumer<Boolean> onChange;

    public AnimatedToggleSwitch(boolean initialState) {
        this.enabled = initialState;

        this.setPrefSize(WIDTH, HEIGHT);
        this.setMinSize(WIDTH, HEIGHT);
        this.setMaxSize(WIDTH, HEIGHT);
        this.setCursor(Cursor.HAND);

        var bgRect = new Rectangle(WIDTH, HEIGHT);
        bgRect.setArcWidth(HEIGHT);
        bgRect.setArcHeight(HEIGHT);
        bgRect.setFill(initialState ? Theme.TOGGLE_BG_ON : Theme.TOGGLE_BG_OFF);

        var thumb = new Circle(THUMB_RADIUS, Theme.TOGGLE_THUMB);
        thumb.setCenterY(HEIGHT / 2.0);

        // Calculate min and max X positions for the thumb
        var minX = 2.0 + THUMB_RADIUS;
        var maxX = WIDTH - 2.0 - THUMB_RADIUS;
        thumb.setCenterX(initialState ? maxX : minX);

        this.getChildren().addAll(bgRect, thumb);

        timeline = new Timeline();

        this.setOnMouseClicked(e -> toggle(bgRect, thumb, minX, maxX));
    }

    private void toggle(Rectangle bgRect, Circle thumb, double minX, double maxX) {
        enabled = !enabled;

        timeline.stop();
        timeline.getKeyFrames().clear();

        var targetColor = enabled ? Theme.TOGGLE_BG_ON : Theme.TOGGLE_BG_OFF;
        var targetX = enabled ? maxX : minX;

        timeline.getKeyFrames().add(
                new KeyFrame(Duration.millis(200),
                        new KeyValue(bgRect.fillProperty(), targetColor),
                        new KeyValue(thumb.centerXProperty(), targetX)
                )
        );

        timeline.play();

        if (onChange != null) {
            onChange.accept(enabled);
        }
    }
}
