package to.sparkapp.app.ui.topbar.components;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;

/** A small ember-shaped face, drawn locally without image assets. */
final class MascotFace extends Group {
    private final Group eyes;
    private final MascotGears gears = new MascotGears();

    MascotFace() {
        var bounds = new Rectangle(40, 44, Color.TRANSPARENT);
        var body = new Rectangle(4, 11, 32, 28);
        body.setArcWidth(20);
        body.setArcHeight(20);
        body.setFill(Color.web("#45434a"));
        body.setStroke(Color.web("#b19470", 0.8));

        var spark = new SVGPath();
        spark.setContent("M 16 12 C 16 9 18 8 20 5 C 22 8 24 9 24 12 Z");
        spark.setFill(Color.web("#dfb584"));

        var left = new Ellipse(14, 25, 2.6, 4.5);
        var right = new Ellipse(26, 25, 2.6, 4.5);
        left.setFill(Color.web("#f7e7ca"));
        right.setFill(left.getFill());
        eyes = new Group(left, right);

        getChildren().addAll(bounds, spark, body, gears, eyes);
    }

    void look(double x, double y) {
        eyes.setTranslateX(Math.clamp(x, -2.3, 2.3));
        eyes.setTranslateY(Math.clamp(y, -1.7, 1.7));
    }

    void blink(double openness) {
        eyes.setScaleY(openness);
    }

    void blendGears(double amount) {
        eyes.setOpacity(1 - amount);
        gears.setOpacity(amount);
    }

    void turnGears(double degrees) {
        gears.turn(degrees);
    }
}
