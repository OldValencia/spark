package to.sparkapp.app.ui.topbar.components;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Shape;

/** Two coarse six-tooth glyphs remain readable at the mascot's native size. */
final class MascotGears extends Group {
    private final Group left = gear(14);
    private final Group right = gear(26);

    MascotGears() {
        getStyleClass().add("mascot-gears");
        getChildren().addAll(left, right);
        setOpacity(0);
        setMouseTransparent(true);
    }

    private Group gear(double x) {
        var outline = new Polygon();

        for (var point = 0; point < 24; point++) {
            var angle = Math.toRadians(point * 15);
            var radius = point % 4 == 1 || point % 4 == 2 ? 5.6 : 4.2;
            outline.getPoints().addAll(Math.cos(angle) * radius, Math.sin(angle) * radius);
        }

        var ring = Shape.subtract(outline, new Circle(1.9));
        ring.setFill(Color.web("#f7e7ca"));
        var glyph = new Group(ring);
        glyph.setTranslateX(x);
        glyph.setTranslateY(25);

        return glyph;
    }

    void turn(double degrees) {
        left.setRotate(degrees);
        right.setRotate(-degrees);
    }
}
