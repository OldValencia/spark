package to.sparkapp.app.windows;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.stage.Stage;
import javafx.util.Duration;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.windows.WindowController.Visibility;

/** Animates the native window position while keeping saved geometry at rest. */
final class WindowMotion {
    private static final Interpolator EASE = Interpolator.SPLINE(0.2, 0.8, 0.2, 1);
    private final Stage stage;
    private final AppPreferences preferences;
    private final BooleanSupplier docked;
    private final DoubleSupplier top;
    private final Consumer<Visibility> state;
    private final Timeline animation = new Timeline();
    private final SimpleDoubleProperty y = new SimpleDoubleProperty();
    private double restingY;
    private boolean closing;
    private Runnable completion;

    WindowMotion(Stage stage, AppPreferences preferences, BooleanSupplier docked, DoubleSupplier top, Consumer<Visibility> state) {
        this.stage = stage;
        this.preferences = preferences;
        this.docked = docked;
        this.top = top;
        this.state = state;
        y.addListener((observable, before, value) -> stage.setY(value.doubleValue()));
        animation.setOnFinished(event -> finish());
    }

    boolean running() {
        return animation.getStatus() == Animation.Status.RUNNING;
    }

    double restingY() {
        return running() ? restingY : stage.getY();
    }

    void open() {
        boolean reversing = running();

        if (!reversing) {
            restingY = stage.getY();
        }

        if (!reversing && !preferences.isReducedMotion()) {
            stage.setY(hiddenY());
        }

        closing = false;
        move(restingY, 200, () -> state.accept(Visibility.VISIBLE));
        state.accept(running() ? Visibility.OPENING : Visibility.VISIBLE);
    }

    void hide(Runnable completed) {
        if (!running()) {
            restingY = stage.getY();
        }

        closing = true;
        state.accept(Visibility.CLOSING);
        move(hiddenY(), 140, () -> {
            stage.setY(restingY);
            completed.run();
        });
    }

    private double hiddenY() {
        return docked.getAsBoolean() ? top.getAsDouble() - stage.getHeight() - 2 : restingY - 8;
    }

    private void move(double target, double milliseconds, Runnable completed) {
        animation.stop();
        completion = completed;

        if (preferences.isReducedMotion()) {
            stage.setY(target);
            finish();

            return;
        }

        y.set(stage.getY());
        animation.getKeyFrames().setAll(new KeyFrame(Duration.ZERO, new KeyValue(y, stage.getY())),
                new KeyFrame(Duration.millis(milliseconds), new KeyValue(y, target, EASE)));
        animation.playFromStart();
    }

    private void finish() {
        var action = completion;
        completion = null;

        if (action != null) {
            action.run();
        }
    }

    void settle() {
        if (!running()) {
            return;
        }

        animation.stop();
        completion = null;
        stage.setY(restingY);
        closing = false;
        state.accept(stage.isShowing() ? Visibility.VISIBLE : Visibility.HIDDEN);
    }

    void refresh() {
        if (preferences.isReducedMotion() && running()) {
            if (closing) {
                animation.stop();
                stage.setY(hiddenY());
                finish();
            } else {
                settle();
            }
        }
    }
}
