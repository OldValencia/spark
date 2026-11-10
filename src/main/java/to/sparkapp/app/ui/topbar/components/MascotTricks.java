package to.sparkapp.app.ui.topbar.components;

import java.util.List;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.util.Duration;

/** The longer reactions: joy, surprise, a quiet "shh", a dizzy spin and a goodbye. */
final class MascotTricks {
    private final MascotFace face;
    private final DoubleProperty openness;
    private final DoubleProperty joy = new SimpleDoubleProperty();
    private final DoubleProperty surprise = new SimpleDoubleProperty();
    private final DoubleProperty dizziness = new SimpleDoubleProperty();
    private final DoubleProperty spiral = new SimpleDoubleProperty();
    private final DoubleProperty sadness = new SimpleDoubleProperty();

    /**
     * @param openness the eyes' openness, shared with the short reactions
     */
    MascotTricks(MascotFace face, DoubleProperty openness) {
        this.face = face;
        this.openness = openness;
        joy.addListener((observable, before, value) -> face.beam(value.doubleValue()));
        surprise.addListener((observable, before, value) -> face.gasp(value.doubleValue()));
        dizziness.addListener((observable, before, value) -> face.dizzy(value.doubleValue()));
        spiral.addListener((observable, before, value) -> face.turnSpirals(value.doubleValue()));
        sadness.addListener((observable, before, value) -> face.frown(value.doubleValue()));
    }

    static boolean plays(MascotReaction reaction) {
        return switch (reaction) {
            case HAPPY, SURPRISED, SHUSH, DIZZY, FAREWELL -> true;
            default -> false;
        };
    }

    List<KeyFrame> frames(MascotReaction reaction) {
        return switch (reaction) {
            case HAPPY -> happy();
            case SURPRISED -> surprised();
            case SHUSH -> shush();
            case DIZZY -> dizzy();
            case FAREWELL -> farewell();
            default -> throw new IllegalArgumentException(reaction + " is a short reaction");
        };
    }

    void reset() {
        for (var property : List.of(joy, surprise, dizziness, spiral, sadness)) {
            property.set(0);
        }

        face.beam(0);
        face.gasp(0);
        face.dizzy(0);
        face.turnSpirals(0);
        face.frown(0);
    }

    /** Happy eyes, a smile and two little hops. */
    private List<KeyFrame> happy() {
        var y = face.translateYProperty();

        return List.of(at(0, start(joy), start(y)),
                at(110, ease(joy, 1), ease(y, -4)),
                at(230, ease(y, 0)),
                at(340, ease(y, -2.5)),
                at(450, ease(y, 0)),
                at(1_000, new KeyValue(joy, 1)),
                at(1_250, ease(joy, 0)));
    }

    /** Wide eyes and a round mouth, with a small jump back. */
    private List<KeyFrame> surprised() {
        var y = face.translateYProperty();
        var scale = face.scaleXProperty();

        return List.of(at(0, start(surprise), start(y), new KeyValue(scale, 1)),
                at(90, ease(surprise, 1), ease(y, -3), ease(scale, 1.06)),
                at(260, ease(y, 0), ease(scale, 1)),
                at(650, new KeyValue(surprise, 1)),
                at(880, ease(surprise, 0)));
    }

    /** Narrowed, content eyes while the sound goes quiet. */
    private List<KeyFrame> shush() {
        var scale = face.scaleYProperty();
        var tilt = face.rotateProperty();

        return List.of(at(0, new KeyValue(openness, 1), new KeyValue(scale, 1), start(tilt)),
                at(140, ease(openness, 0.25), ease(scale, 0.94), ease(tilt, -5)),
                at(750, new KeyValue(openness, 0.25), new KeyValue(tilt, -5)),
                at(950, ease(openness, 1), ease(scale, 1), ease(tilt, 0)));
    }

    /** Two quick turns, then spirals for eyes and a wobble. */
    private List<KeyFrame> dizzy() {
        var tilt = face.rotateProperty();

        return List.of(at(0, start(tilt), start(dizziness), start(spiral)),
                at(150, ease(dizziness, 1)),
                at(800, new KeyValue(tilt, 720, Interpolator.EASE_OUT)),
                at(1_050, ease(tilt, 728)),
                at(1_300, ease(tilt, 714)),
                at(1_550, ease(tilt, 720)),
                at(1_560, new KeyValue(tilt, 0, Interpolator.DISCRETE)),
                at(1_900, new KeyValue(dizziness, 1), new KeyValue(spiral, 1_080, Interpolator.LINEAR)),
                at(2_150, ease(dizziness, 0)));
    }

    /** A sad little sway from side to side, like a wave. */
    private List<KeyFrame> farewell() {
        var tilt = face.rotateProperty();

        return List.of(at(0, start(sadness), start(tilt)),
                at(180, ease(sadness, 0.7), ease(tilt, -10)),
                at(420, ease(tilt, 10)),
                at(660, ease(tilt, -8)),
                at(900, ease(tilt, 0)),
                at(1_250, new KeyValue(sadness, 0.7)),
                at(1_500, ease(sadness, 0)));
    }

    private static KeyFrame at(double millis, KeyValue... values) {
        return new KeyFrame(Duration.millis(millis), values);
    }

    private static KeyValue start(DoubleProperty property) {
        return new KeyValue(property, 0);
    }

    private static KeyValue ease(DoubleProperty property, double target) {
        return new KeyValue(property, target, Interpolator.EASE_BOTH);
    }
}
