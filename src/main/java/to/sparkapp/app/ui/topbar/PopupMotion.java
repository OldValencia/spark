package to.sparkapp.app.ui.topbar;

import java.util.function.BooleanSupplier;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.stage.Popup;
import javafx.stage.WindowEvent;
import javafx.util.Duration;

/** Moves the owned popup itself so its surface is never clipped by its scene. */
final class PopupMotion {
    private final Popup popup;
    private final BooleanSupplier reducedMotion;
    private final Timeline reveal = new Timeline();
    private final SimpleDoubleProperty y = new SimpleDoubleProperty();
    private double targetY;

    PopupMotion(Popup popup, BooleanSupplier reducedMotion) {
        this.popup = popup;
        this.reducedMotion = reducedMotion;
        y.addListener((observable, before, value) -> popup.setY(value.doubleValue()));
        popup.addEventHandler(WindowEvent.WINDOW_SHOWN, event -> show());
        popup.addEventHandler(WindowEvent.WINDOW_HIDDEN, event -> settle());
    }

    private void show() {
        reveal.stop();
        targetY = popup.getY();

        if (reducedMotion.getAsBoolean()) {
            popup.setOpacity(1);

            return;
        }

        y.set(targetY - 5);
        popup.setOpacity(0);
        reveal.getKeyFrames().setAll(new KeyFrame(Duration.ZERO,
                        new KeyValue(y, targetY - 5), new KeyValue(popup.opacityProperty(), 0)),
                new KeyFrame(Duration.millis(150), new KeyValue(y, targetY, Interpolator.EASE_OUT),
                        new KeyValue(popup.opacityProperty(), 1, Interpolator.EASE_OUT)));
        reveal.playFromStart();
    }

    void settle() {
        if (reveal.getStatus() == Timeline.Status.RUNNING) {
            reveal.stop();
            popup.setY(targetY);
        }

        popup.setOpacity(1);
    }
}
