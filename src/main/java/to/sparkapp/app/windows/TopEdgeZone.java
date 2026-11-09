package to.sparkapp.app.windows;

import java.util.function.Supplier;

import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;

/**
 * The top-center strip of Spark's monitor where the pill sits and where edge gestures start.
 */
final class TopEdgeZone {
    private static final double PILL_HALF_WIDTH = 110;

    private final Supplier<Rectangle2D> monitor;

    TopEdgeZone(Supplier<Rectangle2D> monitor) {
        this.monitor = monitor;
    }

    Screen screen() {
        return Screen.getScreens().stream().filter(
                screen -> screen.getVisualBounds().equals(monitor.get())).findFirst().orElse(Screen.getPrimary());
    }

    boolean nearPill(double x, double y, boolean includePill) {
        return nearTopEdge(x, y, includePill, PILL_HALF_WIDTH);
    }

    boolean nearTopEdge(double x, double y, boolean includePill, double halfWidth) {
        var bounds = screen().getBounds();
        var center = monitor.get().getMinX() + monitor.get().getWidth() / 2;
        var bottom = includePill ? monitor.get().getMinY() + 36 : bounds.getMinY() + 10;

        return Math.abs(x - center) <= halfWidth && y >= bounds.getMinY() && y <= bottom;
    }
}
