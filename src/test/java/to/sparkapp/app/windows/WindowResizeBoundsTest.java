package to.sparkapp.app.windows;

import static org.junit.jupiter.api.Assertions.assertEquals;

import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import org.junit.jupiter.api.Test;

class WindowResizeBoundsTest {
    private final Rectangle2D monitor = new Rectangle2D(0, 0, 1920, 1080);
    private final WindowDragOrigin origin = new WindowDragOrigin(100, 100, 200, 100, 860, 700);

    @Test
    void resizingWestKeepsTheRightEdgeFixed() {
        var bounds = WindowResizeBounds.calculate(origin, new Point2D(150, 100), "w", monitor, false);
        assertEquals(new Rectangle2D(250, 100, 810, 700), bounds);
        assertEquals(origin.x() + origin.width(), bounds.getMaxX());
    }

    @Test
    void dockedResizeChangesWidthSymmetrically() {
        var east = WindowResizeBounds.calculate(origin, new Point2D(150, 100), "e", monitor, true);
        var west = WindowResizeBounds.calculate(origin, new Point2D(50, 100), "w", monitor, true);
        assertEquals(960, east.getWidth());
        assertEquals(east.getWidth(), west.getWidth());
    }

    @Test
    void cornerResizeKeepsTheOppositeCornerAndEnforcesMinimumSize() {
        var bounds = WindowResizeBounds.calculate(origin, new Point2D(2000, 2000), "nw", monitor, false);
        assertEquals(420, bounds.getWidth());
        assertEquals(320, bounds.getHeight());
        assertEquals(origin.x() + origin.width(), bounds.getMaxX());
        assertEquals(origin.y() + origin.height(), bounds.getMaxY());
    }

    @Test
    void resizeStopsAtTheMonitorBoundary() {
        var bounds = WindowResizeBounds.calculate(origin, new Point2D(4000, 4000), "se", monitor, false);
        assertEquals(monitor.getMaxX(), bounds.getMaxX());
        assertEquals(monitor.getMaxY(), bounds.getMaxY());
    }
}
