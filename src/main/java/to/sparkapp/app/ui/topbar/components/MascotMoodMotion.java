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
 * Shows a mood on the face. Gears turn only while the mascot is thinking; every other change is a
 * short transition.
 */
final class MascotMoodMotion {
    private static final Duration CHANGE = Duration.millis(220);
    private static final Duration GEAR_TURN = Duration.millis(1600);

    private final MascotFace face;
    private final DoubleProperty gears = new SimpleDoubleProperty();
    private final DoubleProperty sadness = new SimpleDoubleProperty();
    private final DoubleProperty sleepiness = new SimpleDoubleProperty();
    private final DoubleProperty spin = new SimpleDoubleProperty();
    private final Timeline change = new Timeline();
    private final Timeline spinning;

    MascotMoodMotion(MascotFace face) {
        this.face = face;
        gears.addListener((observable, before, value) -> face.blendGears(value.doubleValue()));
        sadness.addListener((observable, before, value) -> face.frown(value.doubleValue()));
        sleepiness.addListener((observable, before, value) -> face.doze(value.doubleValue()));
        spin.addListener((observable, before, value) -> face.turnGears(value.doubleValue()));
        spinning = new Timeline(new KeyFrame(Duration.ZERO, new KeyValue(spin, 0)),
                new KeyFrame(GEAR_TURN, new KeyValue(spin, 360, Interpolator.LINEAR)));
        spinning.setCycleCount(Animation.INDEFINITE);
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
    }

    /** Returns the face to neutral at once, for example before a reaction. */
    void clear() {
        change.stop();
        spinning.stop();
        gears.set(0);
        sadness.set(0);
        sleepiness.set(0);
        spin.set(0);
        face.blendGears(0);
        face.frown(0);
        face.doze(0);
        face.turnGears(0);
    }

    boolean isSpinning() {
        return spinning.getStatus() == Animation.Status.RUNNING;
    }

    private static KeyValue target(DoubleProperty property, boolean on) {
        return new KeyValue(property, on ? 1 : 0, Interpolator.EASE_BOTH);
    }
}
