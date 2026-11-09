package to.sparkapp.app.ui.topbar.components;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;

/** A small ember-shaped face, drawn locally without image assets. */
final class MascotFace extends Group {
    private static final Color GLOW = Color.web("#f7e7ca");
    private static final double SAD_EYES = 0.35;
    private static final double SLEEPY_EYES = 0.88;

    private final Group eyes;
    private final MascotGears gears = new MascotGears();
    private final SVGPath mouth = stroke("M 16.5 34 Q 20 31.2 23.5 34", GLOW);
    private final SVGPath sleepMark = stroke("M 31 5 H 35 L 31 10 H 35", Color.web("#dfb584"));
    private double openness = 1;
    private double sadness;
    private double sleepiness;

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
        left.setFill(GLOW);
        right.setFill(left.getFill());
        eyes = new Group(left, right);
        mouth.setOpacity(0);
        sleepMark.setOpacity(0);

        // The eyes stay last: tests and the pointer gaze look them up there.
        getChildren().addAll(bounds, spark, body, gears, mouth, sleepMark, eyes);
    }

    void look(double x, double y) {
        eyes.setTranslateX(Math.clamp(x, -2.3, 2.3));
        eyes.setTranslateY(Math.clamp(y, -1.7, 1.7));
    }

    void blink(double openness) {
        this.openness = openness;
        updateEyes();
    }

    void blendGears(double amount) {
        eyes.setOpacity(1 - amount);
        gears.setOpacity(amount);
    }

    void turnGears(double degrees) {
        gears.turn(degrees);
    }

    /** Narrowed eyes and a small frown. */
    void frown(double amount) {
        sadness = amount;
        mouth.setOpacity(amount);
        updateEyes();
    }

    /** Closed eyes and a little "z". */
    void doze(double amount) {
        sleepiness = amount;
        sleepMark.setOpacity(amount);
        updateEyes();
    }

    private void updateEyes() {
        eyes.setScaleY(openness * (1 - SAD_EYES * sadness) * (1 - SLEEPY_EYES * sleepiness));
    }

    private static SVGPath stroke(String path, Color color) {
        var shape = new SVGPath();
        shape.setContent(path);
        shape.setFill(Color.TRANSPARENT);
        shape.setStroke(color);
        shape.setStrokeWidth(1.4);
        shape.setStrokeLineCap(StrokeLineCap.ROUND);
        shape.setMouseTransparent(true);

        return shape;
    }
}
