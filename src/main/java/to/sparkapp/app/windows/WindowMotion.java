package to.sparkapp.app.windows;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;

import javafx.animation.Interpolator;
import javafx.stage.Stage;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.windows.WindowController.Visibility;

/** Animates the native window position while keeping saved geometry at rest. */
final class WindowMotion {
    private static final Interpolator OPEN_EASE = Interpolator.SPLINE(0.2, 0, 0.2, 1);
    private static final Interpolator CLOSE_EASE = Interpolator.SPLINE(0.4, 0, 0.8, 1);
    private final Stage stage;
    private final AppPreferences preferences;
    private final BooleanSupplier docked;
    private final DoubleSupplier top;
    private final Consumer<Visibility> state;
    private final WindowPositionAnimation animation;
    private double restingY;
    private double restingOpacity;
    private boolean closing;
    private boolean prepared;
    private Runnable completion;

    WindowMotion(Stage stage, AppPreferences preferences, BooleanSupplier docked, DoubleSupplier top, Consumer<Visibility> state) {
        this.stage = stage;
        this.preferences = preferences;
        this.docked = docked;
        this.top = top;
        this.state = state;
        animation = new WindowPositionAnimation(stage::setY);
    }

    boolean running() {
        return animation.running();
    }

    double restingY() {
        return prepared || running() ? restingY : stage.getY();
    }

    void prepareOpen() {
        if (prepared || running()) {
            return;
        }

        restingY = stage.getY();
        restingOpacity = stage.getOpacity();
        prepared = true;

        if (!preferences.isReducedMotion()) {
            stage.setOpacity(0);
            stage.setY(hiddenY());
        }
    }

    void open() {
        prepareOpen();

        if (prepared) {
            // Native creation may adjust the bounds supplied before the first show.
            stage.setY(preferences.isReducedMotion() ? restingY : hiddenY());
            stage.setOpacity(restingOpacity);
        }

        prepared = false;

        closing = false;
        move(restingY, 340, OPEN_EASE, () -> state.accept(Visibility.VISIBLE));
        state.accept(running() ? Visibility.OPENING : Visibility.VISIBLE);
    }

    void hide(Runnable completed) {
        if (!running()) {
            restingY = stage.getY();
        }

        closing = true;
        state.accept(Visibility.CLOSING);
        move(hiddenY(), 200, CLOSE_EASE, () -> {
            completed.run();
            stage.setY(restingY);
        });
    }

    private double hiddenY() {
        return docked.getAsBoolean() ? top.getAsDouble() - stage.getHeight() - 2 : restingY - 8;
    }

    private void move(double target, double milliseconds, Interpolator easing, Runnable completed) {
        animation.stop();
        completion = completed;

        if (preferences.isReducedMotion()) {
            stage.setY(target);
            finish();

            return;
        }

        animation.play(stage.getY(), target, milliseconds, easing, this::finish);
    }

    private void finish() {
        var action = completion;
        completion = null;

        if (action != null) {
            action.run();
        }
    }

    void settle() {
        if (!prepared && !running()) {
            return;
        }

        animation.stop();
        completion = null;
        if (prepared) {
            stage.setOpacity(restingOpacity);
        }
        prepared = false;
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
