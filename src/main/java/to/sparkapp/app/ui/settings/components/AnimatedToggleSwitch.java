package to.sparkapp.app.ui.settings.components;

import java.util.function.Consumer;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.Cursor;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import lombok.Getter;
import lombok.Setter;
import to.sparkapp.app.ui.Theme;

public class AnimatedToggleSwitch extends Pane {

    private static final double WIDTH = 36;
    private static final double HEIGHT = 22;
    private static final double THUMB_RADIUS = 8;
    private static final double INSET = 3;
    private static final Duration ANIM = Duration.millis(180);

    @Getter
    private boolean selected;
    private final Timeline timeline;
    private final Rectangle bgRect;
    private final Circle thumb;

    @Setter
    private Consumer<Boolean> onChange;

    public AnimatedToggleSwitch(boolean initialState) {
        this.selected = initialState;

        this.setPrefSize(WIDTH, HEIGHT);
        this.setMinSize(WIDTH, HEIGHT);
        this.setMaxSize(WIDTH, HEIGHT);
        this.setCursor(Cursor.HAND);

        bgRect = new Rectangle(WIDTH, HEIGHT);
        bgRect.setArcWidth(HEIGHT);
        bgRect.setArcHeight(HEIGHT);
        bgRect.setFill(initialState ? Theme.TOGGLE_BG_ON : Theme.TOGGLE_BG_OFF);

        thumb = new Circle(THUMB_RADIUS, Theme.TOGGLE_THUMB);
        thumb.setCenterY(HEIGHT / 2.0);
        thumb.setEffect(new DropShadow(4, 0, 1, Color.rgb(0, 0, 0, 0.35)));

        var minX = INSET + THUMB_RADIUS;
        var maxX = WIDTH - INSET - THUMB_RADIUS;
        thumb.setCenterX(initialState ? maxX : minX);

        this.getChildren().addAll(bgRect, thumb);

        timeline = new Timeline();

        this.setOnMouseClicked(e -> toggle());
    }

    private void toggle() {
        if (isDisabled()) {
            return;
        }

        setSelected(!selected);
        if (onChange != null) {
            onChange.accept(selected);
        }
    }

    // Dependent settings can reset the display without submitting another preference change.
    public void setSelected(boolean selected) {
        if (this.selected == selected) {
            return;
        }

        this.selected = selected;

        timeline.stop();
        timeline.getKeyFrames().clear();

        var targetColor = selected ? Theme.TOGGLE_BG_ON : Theme.TOGGLE_BG_OFF;
        var targetX = selected ? WIDTH - INSET - THUMB_RADIUS : INSET + THUMB_RADIUS;

        timeline.getKeyFrames().add(
                new KeyFrame(ANIM, new KeyValue(bgRect.fillProperty(), targetColor, Interpolator.EASE_BOTH),
                        new KeyValue(thumb.centerXProperty(), targetX, Interpolator.EASE_BOTH)));

        timeline.play();
    }
}
