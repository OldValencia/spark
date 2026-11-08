package to.sparkapp.app.ui.topbar.components;

import java.util.function.BooleanSupplier;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Point2D;
import javafx.scene.layout.StackPane;
import javafx.stage.Popup;
import javafx.stage.Window;
import javafx.util.Duration;

/**
 * The mascot leaving the Spark bar to show the way: a small window that flies along an arc and
 * looks at whatever it is talking about. It flies over the page, which the bar cannot draw on.
 */
public final class MascotGuide extends Popup {
    public static final double WIDTH = 40;
    public static final double HEIGHT = 44;
    private static final Duration FLIGHT = Duration.millis(520);
    private static final Duration LANDING = Duration.millis(160);
    private static final Interpolator GLIDE = Interpolator.SPLINE(0.3, 0, 0.2, 1);
    private static final double ARC_HEIGHT = 26;
    private static final double TILT = 10;

    private final BooleanSupplier reducedMotion;
    private final MascotFace face = new MascotFace();
    private final DoubleProperty progress = new SimpleDoubleProperty();
    private final Timeline flight = new Timeline();
    private final Timeline landing = new Timeline();
    private Point2D from = Point2D.ZERO;
    private Point2D to = Point2D.ZERO;

    public MascotGuide(BooleanSupplier reducedMotion) {
        this.reducedMotion = reducedMotion;
        var body = new StackPane(face);
        body.setMinSize(WIDTH, HEIGHT);
        body.setMaxSize(WIDTH, HEIGHT);
        body.setStyle("-fx-background-color: transparent;");
        body.setMouseTransparent(true);
        getContent().add(body);
        setAutoHide(false);
        progress.addListener((observable, before, value) -> step(value.doubleValue()));
    }

    /** Appears exactly where the mascot sits in the bar, so it seems to step out of it. */
    public void appear(Window owner, Point2D position) {
        to = position;
        show(owner, position.getX(), position.getY());
        place(position);
    }

    /**
     * Flies to a screen position and then looks at a point.
     *
     * @param landed runs once the guide has arrived
     */
    public void flyTo(Point2D position, Point2D lookAt, Runnable landed) {
        flight.stop();
        landing.stop();
        from = new Point2D(getX(), getY());
        to = position;
        face.look(0, 0);

        if (reducedMotion.getAsBoolean() || from.distance(to) < 1) {
            settle(lookAt, landed);

            return;
        }

        progress.set(0);
        flight.getKeyFrames().setAll(new KeyFrame(FLIGHT, new KeyValue(progress, 1, GLIDE)));
        flight.setOnFinished(event -> {
            settle(lookAt, landed);
            land();
        });
        flight.playFromStart();
    }

    /** Points the eyes at a screen position, such as the button the tour talks about. */
    public void lookAt(Point2D target) {
        if (target == null) {
            face.look(0, 0);

            return;
        }

        var center = new Point2D(getX() + WIDTH / 2, getY() + HEIGHT / 2);
        var direction = target.subtract(center);

        if (direction.magnitude() < 1) {
            face.look(0, 0);

            return;
        }

        var unit = direction.normalize();
        face.look(unit.getX() * 2.3, unit.getY() * 1.7);
    }

    /** Moves without flying, for example when the Spark window is dragged during the tour. */
    public void place(Point2D position) {
        flight.stop();
        to = position;
        setX(position.getX());
        setY(position.getY());
        face.setRotate(0);
    }

    public void dismiss() {
        flight.stop();
        landing.stop();
        hide();
    }

    boolean isFlying() {
        return flight.getStatus() == Animation.Status.RUNNING;
    }

    private void settle(Point2D lookAt, Runnable landed) {
        place(to);
        lookAt(lookAt);

        if (landed != null) {
            landed.run();
        }
    }

    private void step(double share) {
        var x = from.getX() + (to.getX() - from.getX()) * share;
        var y = from.getY() + (to.getY() - from.getY()) * share - Math.sin(Math.PI * share) * ARC_HEIGHT;
        setX(x);
        setY(y);
        face.setRotate(Math.sin(Math.PI * share) * TILT * Math.signum(to.getX() - from.getX()));
    }

    /** A small squash as the guide touches down. */
    private void land() {
        if (reducedMotion.getAsBoolean()) {
            return;
        }

        landing.getKeyFrames().setAll(
                new KeyFrame(Duration.ZERO, new KeyValue(face.scaleYProperty(), 0.9),
                        new KeyValue(face.scaleXProperty(), 1.06)),
                new KeyFrame(LANDING, new KeyValue(face.scaleYProperty(), 1, Interpolator.EASE_OUT),
                        new KeyValue(face.scaleXProperty(), 1, Interpolator.EASE_OUT)));
        landing.playFromStart();
    }
}
