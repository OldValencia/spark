package to.sparkapp.app.ui.topbar.components;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.DoubleProperty;
import javafx.util.Duration;

/** Brief expressions replace one another rather than building an animation queue. */
final class MascotMotion {
    private final MascotFace face;
    private final Timeline reaction = new Timeline();
    private final SimpleDoubleProperty openness = new SimpleDoubleProperty(1);
    private final SimpleDoubleProperty gearBlend = new SimpleDoubleProperty();
    private final SimpleDoubleProperty gearAngle = new SimpleDoubleProperty();
    private final MascotTricks tricks;
    private MascotReaction active;
    private long lastResize;

    /**
     * @param settled runs when a reaction has finished, so a lasting mood can return
     */
    MascotMotion(MascotFace face, Runnable settled) {
        this.face = face;
        tricks = new MascotTricks(face, openness);
        reaction.setOnFinished(event -> settled.run());
        openness.addListener((observable, before, value) -> face.blink(value.doubleValue()));
        gearBlend.addListener((observable, before, value) -> face.blendGears(value.doubleValue()));
        gearAngle.addListener((observable, before, value) -> face.turnGears(value.doubleValue()));
    }

    void play(MascotReaction expression) {
        if (expression == active && reaction.getStatus() == Animation.Status.RUNNING) {
            return;
        }

        if (expression == MascotReaction.RESIZE) {
            var now = System.nanoTime();

            if (now - lastResize < 450_000_000L) {
                return;
            }

            lastResize = now;
        }

        reset();
        active = expression;

        if (MascotTricks.plays(expression)) {
            reaction.getKeyFrames().setAll(tricks.frames(expression));
            reaction.playFromStart();

            return;
        }

        if (expression == MascotReaction.SETTINGS) {
            settings();

            return;
        }

        if (expression == MascotReaction.WAKE) {
            wake();

            return;
        }

        var pose = switch (expression) {
            case HOVER -> new Pose(4, -1, 0, 1, 1, 1.08);
            case GREETING -> new Pose(-7, -3, 0, 1, 1, 0.15);
            case SITES -> new Pose(7, -1, 1, 1, 1, 1.18);
            case SETTINGS, WAKE, HAPPY, SURPRISED, SHUSH, DIZZY, FAREWELL ->
                    throw new IllegalStateException(expression + " has its own animation");
            case RESIZE -> new Pose(0, 0, 0, 1.08, 0.93, 1);
            case SITE_SWITCH -> new Pose(0, 2, 0, 1, 1, 0.12);
        };
        reaction.getKeyFrames().setAll(new KeyFrame(Duration.ZERO,
                        new KeyValue(openness, 1), new KeyValue(face.rotateProperty(), 0),
                        new KeyValue(face.translateXProperty(), 0), new KeyValue(face.translateYProperty(), 0),
                        new KeyValue(face.scaleXProperty(), 1), new KeyValue(face.scaleYProperty(), 1)),
                new KeyFrame(Duration.millis(100),
                        value(openness, pose.eyes()), value(face.rotateProperty(), pose.tilt()),
                        value(face.translateXProperty(), pose.x()), value(face.translateYProperty(), pose.y()),
                        value(face.scaleXProperty(), pose.scaleX()), value(face.scaleYProperty(), pose.scaleY())),
                new KeyFrame(Duration.millis(200), value(openness, 1),
                        value(face.rotateProperty(), -pose.tilt() * 0.2),
                        value(face.translateXProperty(), 0), value(face.translateYProperty(), -pose.y() * 0.15),
                        value(face.scaleXProperty(), 1), value(face.scaleYProperty(), 1)),
                new KeyFrame(Duration.millis(360), value(face.rotateProperty(), 0),
                        value(face.translateYProperty(), 0)));
        reaction.playFromStart();
    }

    private void settings() {
        reaction.getKeyFrames().setAll(
                new KeyFrame(Duration.ZERO, new KeyValue(gearBlend, 0), new KeyValue(gearAngle, 0)),
                new KeyFrame(Duration.millis(80), value(gearBlend, 1), new KeyValue(gearAngle, 0)),
                new KeyFrame(Duration.millis(350), new KeyValue(gearBlend, 1),
                        value(gearAngle, 90)),
                new KeyFrame(Duration.millis(460), value(gearBlend, 0), value(gearAngle, 110)));
        reaction.playFromStart();
    }

    /** Eyes open from sleep with a small stretch. */
    private void wake() {
        reaction.getKeyFrames().setAll(
                new KeyFrame(Duration.ZERO, new KeyValue(openness, 0.12), new KeyValue(face.translateYProperty(), 1),
                        new KeyValue(face.scaleYProperty(), 1)),
                new KeyFrame(Duration.millis(140), value(openness, 0.35)),
                new KeyFrame(Duration.millis(260), value(openness, 1), value(face.translateYProperty(), -2),
                        value(face.scaleYProperty(), 1.06)),
                new KeyFrame(Duration.millis(420), value(face.translateYProperty(), 0),
                        value(face.scaleYProperty(), 1)));
        reaction.playFromStart();
    }

    boolean isPlaying() {
        return reaction.getStatus() == Animation.Status.RUNNING;
    }

    private KeyValue value(DoubleProperty property, double target) {
        return new KeyValue(property, target, Interpolator.EASE_OUT);
    }

    void reset() {
        reaction.stop();
        face.setRotate(0);
        face.setTranslateX(0);
        face.setTranslateY(0);
        face.setScaleX(1);
        face.setScaleY(1);
        face.look(0, 0);
        openness.set(1);
        face.blink(1);
        gearBlend.set(0);
        gearAngle.set(0);
        face.blendGears(0);
        face.turnGears(0);
        tricks.reset();
    }

    private record Pose(double tilt, double y, double x, double scaleX, double scaleY, double eyes) {
    }
}
