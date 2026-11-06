package to.sparkapp.app.windows;

import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.input.MouseEvent;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class WindowResizeBounds {
    static void apply(WindowDragOrigin origin, MouseEvent event, String edge, WindowGeometry geometry) {
        var pointer = new Point2D(event.getScreenX(), event.getScreenY());
        var bounds = calculate(origin, pointer, edge, geometry.monitor, geometry.isDocked());
        geometry.apply(bounds.getMinX(), bounds.getMinY(), bounds.getWidth(), bounds.getHeight(), geometry.monitor);
    }

    static Rectangle2D calculate(WindowDragOrigin origin, Point2D pointer, String edge, Rectangle2D monitor, boolean docked) {
        var deltaX = pointer.getX() - origin.screenX();
        var deltaY = pointer.getY() - origin.screenY();
        var width = resizedWidth(origin, deltaX, edge, monitor, docked);
        var height = resizedHeight(origin, deltaY, edge, monitor);
        var x = edge.contains("w") && !docked ? origin.x() + origin.width() - width : origin.x();
        var y = edge.contains("n") ? origin.y() + origin.height() - height : origin.y();

        return new Rectangle2D(x, y, width, height);
    }

    private static double resizedWidth(WindowDragOrigin origin, double deltaX, String edge, Rectangle2D monitor, boolean docked) {
        var width = origin.width();
        var multiplier = docked ? 2 : 1;

        if (edge.contains("e")) {
            width += deltaX * multiplier;
        }

        if (edge.contains("w")) {
            width -= deltaX * multiplier;
        }

        var maximum = monitor.getWidth();

        if (!docked) {
            maximum = edge.contains("w") ?
                    origin.x() + origin.width() - monitor.getMinX() : monitor.getMaxX() - origin.x();
        }

        return Math.clamp(width, Math.min(420, monitor.getWidth()), maximum);
    }

    private static double resizedHeight(WindowDragOrigin origin, double deltaY, String edge, Rectangle2D monitor) {
        var height = origin.height();

        if (edge.contains("s")) {
            height += deltaY;
        }

        if (edge.contains("n")) {
            height -= deltaY;
        }

        var maximum = edge.contains("n") ?
                origin.y() + origin.height() - monitor.getMinY() : monitor.getMaxY() - origin.y();

        return Math.clamp(height, Math.min(320, monitor.getHeight()), maximum);
    }
}
