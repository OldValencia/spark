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
    private static final double SURPRISED_EYES = 0.3;

    private final Group eyes;
    private final MascotGears gears = new MascotGears();
    private final MascotFeatures features = new MascotFeatures();
    private final SVGPath mouth = stroke("M 16.5 34 Q 20 31.2 23.5 34", GLOW);
    private final SVGPath sleepMark = stroke("M 31 5 H 35 L 31 10 H 35", Color.web("#dfb584"));
    private double openness = 1;
    private double sadness;
    private double sleepiness;
    private double surprise;
    private double gearBlend;
    private double joy;
    private double dizziness;

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
        getChildren().addAll(bounds, spark, body, gears, mouth, sleepMark, features, eyes);
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
        gearBlend = amount;
        gears.setOpacity(amount);
        updateEyeOpacity();
    }

    /** Happy, closed eyes and a smile. */
    void beam(double amount) {
        joy = amount;
        features.beam(amount);
        updateEyeOpacity();
    }

    /** Wide eyes and a round mouth. */
    void gasp(double amount) {
        surprise = amount;
        features.gasp(amount);
        updateEyes();
    }

    /** Spirals in place of the eyes. */
    void dizzy(double amount) {
        dizziness = amount;
        features.dizzy(amount);
        updateEyeOpacity();
    }

    void turnSpirals(double degrees) {
        features.turnSpirals(degrees);
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
        var wide = 1 + SURPRISED_EYES * surprise;
        eyes.setScaleX(1 + SURPRISED_EYES * surprise / 3);
        eyes.setScaleY(openness * (1 - SAD_EYES * sadness) * (1 - SLEEPY_EYES * sleepiness) * wide);
    }

    private void updateEyeOpacity() {
        eyes.setOpacity((1 - gearBlend) * (1 - joy) * (1 - dizziness));
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
