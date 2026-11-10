package to.sparkapp.app.ui.topbar.components;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.util.Duration;
import to.sparkapp.app.utils.FxTimer;

/**
 * Small signs of life while nothing else happens: a blink every few seconds, sometimes twice, and now and then a look
 * around once the pointer has been still for a while.
 */
final class MascotIdle {
    private static final long STILL_BEFORE_GLANCE_NANOS = 8_000_000_000L;
    private static final double CLOSED = 0.1;

    private final MascotFace face;
    private final BooleanSupplier free;
    private final DoubleProperty lids = new SimpleDoubleProperty(1);
    private final DoubleProperty gazeX = new SimpleDoubleProperty();
    private final DoubleProperty gazeY = new SimpleDoubleProperty();
    private final Timeline blink = new Timeline();
    private final Timeline glance = new Timeline();
    private final FxTimer blinkTimer;
    private final FxTimer glanceTimer;
    private long lastActivity = System.nanoTime();

    /**
     * @param free     whether the face is free for idle motion now: no reaction plays and the mascot is awake
     * @param lookBack turns the eyes back to the pointer once a glance ends
     */
    MascotIdle(MascotFace face, BooleanSupplier free, Runnable lookBack) {
        this.face = face;
        this.free = free;
        glance.setOnFinished(event -> lookBack.run());
        lids.addListener((observable, before, value) -> face.blink(value.doubleValue()));
        gazeX.addListener((observable, before, value) -> face.look(value.doubleValue(), gazeY.get()));
        gazeY.addListener((observable, before, value) -> face.look(gazeX.get(), value.doubleValue()));
        blinkTimer = FxTimer.once(Duration.seconds(4), this::blinkNow);
        glanceTimer = FxTimer.once(Duration.seconds(20), this::glanceNow);
    }

    void setActive(boolean active) {
        if (active) {
            schedule(blinkTimer, 3, 7);
            schedule(glanceTimer, 15, 35);
        } else {
            blinkTimer.stop();
            glanceTimer.stop();
            interrupt();
        }
    }

    /** The user moved the pointer or typed: the mascot looks where they are, not around. */
    void activity() {
        lastActivity = System.nanoTime();

        glance.stop();
    }

    /** Stops a blink or a glance at once, for example when a reaction starts. */
    void interrupt() {
        blink.stop();
        glance.stop();
        lids.set(1);
        gazeX.set(0);
        gazeY.set(0);
    }

    private void blinkNow() {
        if (free.getAsBoolean()) {
            var twice = ThreadLocalRandom.current().nextInt(5) == 0;
            blink.getKeyFrames().setAll(blinkFrames(0));

            if (twice) {
                blink.getKeyFrames().addAll(blinkFrames(230));
            }

            blink.playFromStart();
        }

        schedule(blinkTimer, 3, 7);
    }

    private KeyFrame[] blinkFrames(double startMillis) {
        return new KeyFrame[] {
                new KeyFrame(Duration.millis(startMillis), new KeyValue(lids, 1)),
                new KeyFrame(Duration.millis(startMillis + 70), smooth(lids, CLOSED)),
                new KeyFrame(Duration.millis(startMillis + 160), smooth(lids, 1))
        };
    }

    private void glanceNow() {
        var still = System.nanoTime() - lastActivity >= STILL_BEFORE_GLANCE_NANOS;

        if (still && free.getAsBoolean()) {
            var side = ThreadLocalRandom.current().nextBoolean() ? 2 : -2;
            var height = ThreadLocalRandom.current().nextDouble(-1, 0.6);
            glance.getKeyFrames().setAll(
                    new KeyFrame(Duration.ZERO, new KeyValue(gazeX, 0), new KeyValue(gazeY, 0)),
                    new KeyFrame(Duration.millis(260), smooth(gazeX, side), smooth(gazeY, height)),
                    new KeyFrame(Duration.millis(1_200), new KeyValue(gazeX, side), new KeyValue(gazeY, height)),
                    new KeyFrame(Duration.millis(1_460), smooth(gazeX, 0), smooth(gazeY, 0)));
            glance.playFromStart();
        }

        schedule(glanceTimer, 15, 35);
    }

    private static KeyValue smooth(DoubleProperty property, double target) {
        return new KeyValue(property, target, Interpolator.EASE_BOTH);
    }

    private static void schedule(FxTimer timer, int fromSeconds, int toSeconds) {
        var millis = ThreadLocalRandom.current().nextLong(fromSeconds * 1_000L, toSeconds * 1_000L);
        timer.setDelay(Duration.millis(millis));
        timer.restart();
    }
}
