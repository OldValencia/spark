package to.sparkapp.app.ui.topbar.components;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Rectangle;

/** A small ember-shaped face, drawn locally without image assets. */
final class MascotFace extends Group {
    private final Group eyes;

    MascotFace() {
        var body = new Rectangle(3, 5, 24, 22);
        body.setArcWidth(15);
        body.setArcHeight(15);
        body.setFill(Color.web("#414046"));
        body.setStroke(Color.web("#8a7762", 0.65));

        var spark = new Ellipse(17, 5, 3, 4);
        spark.setRotate(28);
        spark.setFill(Color.web("#d3ae81"));

        var left = new Ellipse(11, 15, 1.8, 3.1);
        var right = new Ellipse(19, 15, 1.8, 3.1);
        left.setFill(Color.web("#f0ddbd"));
        right.setFill(left.getFill());
        eyes = new Group(left, right);

        var leftCheek = new Circle(8, 20, 1.4, Color.web("#d49b80", 0.45));
        var rightCheek = new Circle(22, 20, 1.4, leftCheek.getFill());
        getChildren().addAll(spark, body, leftCheek, rightCheek, eyes);
    }

    void look(double x, double y) {
        eyes.setTranslateX(Math.clamp(x, -1.8, 1.8));
        eyes.setTranslateY(Math.clamp(y, -1.2, 1.2));
    }

    void blink(double openness) {
        eyes.setScaleY(openness);
    }
}
