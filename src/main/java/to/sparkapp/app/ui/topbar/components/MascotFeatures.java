package to.sparkapp.app.ui.topbar.components;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;

/** Features the face shows only for a moment: happy eyes and a smile, a surprised mouth, and dizzy spirals. */
final class MascotFeatures extends Group {
    private static final Color GLOW = Color.web("#f7e7ca");
    private static final String SPIRAL = "M 0 0 A 1 1 0 0 1 1 1 A 2 2 0 0 1 -1 3 A 3 3 0 0 1 -3 0 "
            + "A 3.6 3.6 0 0 1 0.6 -3.6";

    private final Group happy = new Group(
            stroke("M 11.4 26.6 Q 14 21.8 16.6 26.6", 1.8),
            stroke("M 23.4 26.6 Q 26 21.8 28.6 26.6", 1.8),
            stroke("M 16 31.6 Q 20 35.2 24 31.6", 1.4));
    private final Ellipse gasp = new Ellipse(20, 33.4, 1.6, 1.9);
    private final SVGPath leftSpiral = spiral(15, 25.3);
    private final SVGPath rightSpiral = spiral(27, 25.3);
    private final Group dizzy = new Group(leftSpiral, rightSpiral);

    MascotFeatures() {
        gasp.setFill(Color.web("#2b2a2f"));
        gasp.setStroke(GLOW);
        gasp.setStrokeWidth(1.2);

        for (Node feature : new Node[] {happy, gasp, dizzy}) {
            feature.setOpacity(0);
            getChildren().add(feature);
        }

        setMouseTransparent(true);
    }

    void beam(double amount) {
        happy.setOpacity(amount);
    }

    void gasp(double amount) {
        gasp.setOpacity(amount);
    }

    void dizzy(double amount) {
        dizzy.setOpacity(amount);
    }

    void turnSpirals(double degrees) {
        leftSpiral.setRotate(degrees);
        rightSpiral.setRotate(-degrees);
    }

    private static SVGPath spiral(double x, double y) {
        var spiral = stroke(SPIRAL, 1.2);
        spiral.setTranslateX(x);
        spiral.setTranslateY(y);

        return spiral;
    }

    private static SVGPath stroke(String path, double width) {
        var shape = new SVGPath();
        shape.setContent(path);
        shape.setFill(Color.TRANSPARENT);
        shape.setStroke(GLOW);
        shape.setStrokeWidth(width);
        shape.setStrokeLineCap(StrokeLineCap.ROUND);

        return shape;
    }
}
