package to.sparkapp.app.ui.topbar.components;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.util.Duration;

/**
 * Shows a mood on the face. Gears turn only while the mascot is thinking, and it sways only while a site plays
 * sound; every other change is a short transition.
 */
final class MascotMoodMotion {
    private static final Duration CHANGE = Duration.millis(220);
    private static final Duration GEAR_TURN = Duration.millis(1600);
    private static final Duration BEAT = Duration.millis(450);
    private static final double SWAY_DEGREES = 6;

    private final MascotFace face;
    private final DoubleProperty gears = new SimpleDoubleProperty();
    private final DoubleProperty sadness = new SimpleDoubleProperty();
    private final DoubleProperty sleepiness = new SimpleDoubleProperty();
    private final DoubleProperty spin = new SimpleDoubleProperty();
    private final Timeline change = new Timeline();
    private final Timeline spinning;
    private final Timeline swaying;

    MascotMoodMotion(MascotFace face) {
        this.face = face;
        gears.addListener((observable, before, value) -> face.blendGears(value.doubleValue()));
        sadness.addListener((observable, before, value) -> face.frown(value.doubleValue()));
        sleepiness.addListener((observable, before, value) -> face.doze(value.doubleValue()));
        spin.addListener((observable, before, value) -> face.turnGears(value.doubleValue()));
        spinning = new Timeline(new KeyFrame(Duration.ZERO, new KeyValue(spin, 0)),
                new KeyFrame(GEAR_TURN, new KeyValue(spin, 360, Interpolator.LINEAR)));
        spinning.setCycleCount(Animation.INDEFINITE);
        swaying = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(face.rotateProperty(), -SWAY_DEGREES, Interpolator.EASE_BOTH),
                        new KeyValue(face.translateYProperty(), 0, Interpolator.EASE_BOTH)),
                new KeyFrame(BEAT.divide(2), new KeyValue(face.translateYProperty(), -1.5, Interpolator.EASE_BOTH)),
                new KeyFrame(BEAT, new KeyValue(face.rotateProperty(), SWAY_DEGREES, Interpolator.EASE_BOTH),
                        new KeyValue(face.translateYProperty(), 0, Interpolator.EASE_BOTH)));
        swaying.setAutoReverse(true);
        swaying.setCycleCount(Animation.INDEFINITE);
    }

    void show(MascotMood mood) {
        change.stop();
        change.getKeyFrames().setAll(new KeyFrame(CHANGE,
                target(gears, mood == MascotMood.THINKING),
                target(sadness, mood == MascotMood.SAD),
                target(sleepiness, mood == MascotMood.ASLEEP)));
        change.playFromStart();

        if (mood == MascotMood.THINKING) {
            spinning.play();
        } else {
            spinning.stop();
        }

        if (mood == MascotMood.LISTENING) {
            swaying.play();
        } else {
            stopSwaying();
        }
    }

    /** Returns the face to neutral at once, for example before a reaction. */
    void clear() {
        change.stop();
        spinning.stop();
        stopSwaying();
        gears.set(0);
        sadness.set(0);
        sleepiness.set(0);
        spin.set(0);
        face.blendGears(0);
        face.frown(0);
        face.doze(0);
        face.turnGears(0);
    }

    private void stopSwaying() {
        if (swaying.getStatus() != Animation.Status.STOPPED) {
            swaying.stop();
            face.setRotate(0);
            face.setTranslateY(0);
        }
    }

    boolean isSpinning() {
        return spinning.getStatus() == Animation.Status.RUNNING;
    }

    private static KeyValue target(DoubleProperty property, boolean on) {
        return new KeyValue(property, on ? 1 : 0, Interpolator.EASE_BOTH);
    }
}
