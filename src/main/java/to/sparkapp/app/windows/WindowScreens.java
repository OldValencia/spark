package to.sparkapp.app.windows;

import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Finds the screen a window or a point is on. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class WindowScreens {
    /** The usable area of the screen that shows most of the window. */
    static Rectangle2D screenFor(Stage stage) {
        return Screen.getScreensForRectangle(stage.getX(), stage.getY(), Math.max(1, stage.getWidth()),
                        Math.max(1, stage.getHeight())).stream()
                .max((a, b) -> Double.compare(overlap(stage, a.getVisualBounds()), overlap(stage, b.getVisualBounds())))
                .orElse(Screen.getPrimary()).getVisualBounds();
    }

    static Rectangle2D screenAt(double x, double y) {
        return Screen.getScreensForRectangle(x, y, 1, 1).stream().findFirst().orElse(Screen.getPrimary())
                .getVisualBounds();
    }

    private static double overlap(Stage stage, Rectangle2D b) {
        return Math.max(0,
                Math.min(stage.getX() + stage.getWidth(), b.getMaxX()) - Math.max(stage.getX(), b.getMinX())) *
                Math.max(0,
                        Math.min(stage.getY() + stage.getHeight(), b.getMaxY()) - Math.max(stage.getY(), b.getMinY()));
    }
}
