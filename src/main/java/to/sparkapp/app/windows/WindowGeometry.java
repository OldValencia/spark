package to.sparkapp.app.windows;

import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.config.AppPreferences.WindowPlacement;

final class WindowGeometry {
    private final Stage stage;
    private final AppPreferences preferences;
    final ReadOnlyBooleanWrapper docked = new ReadOnlyBooleanWrapper(false);
    private double manualWidth = 860;
    private double manualHeight = 700;
    Rectangle2D monitor;

    WindowGeometry(Stage stage, AppPreferences preferences) {
        this.stage = stage;
        this.preferences = preferences;

        var saved = preferences.getWindowPlacement();
        monitor = Screen.getPrimary().getVisualBounds();

        if (saved != null &&
                finite(saved.x(), saved.y(), saved.width(), saved.height(), saved.manualWidth(), saved.manualHeight(),
                        saved.monitorX(), saved.monitorY()) && saved.width() > 0 && saved.height() > 0) {
            monitor = Screen.getScreens().stream().map(Screen::getVisualBounds)
                    .filter(b -> b.getMinX() == saved.monitorX() && b.getMinY() == saved.monitorY()).findFirst()
                    .orElse(monitor);
            docked.set(saved.docked());
            manualWidth = Math.max(420, saved.manualWidth());
            manualHeight = Math.max(320, saved.manualHeight());
            apply(saved.x(), saved.y(), saved.width(), saved.height(), monitor);
        } else {
            apply(monitor.getMinX() + (monitor.getWidth() - manualWidth) / 2,
                    monitor.getMinY() + (monitor.getHeight() - manualHeight) / 2, manualWidth, manualHeight, monitor);
        }
    }

    boolean isDocked() {
        return docked.get();
    }

    void rememberManualSize() {
        manualWidth = stage.getWidth();
        manualHeight = stage.getHeight();
    }

    public void dock() {
        dockTo(screenForWindow());
    }

    public void toggleDock() {
        if (!isDocked()) {
            dock();

            return;
        }

        docked.set(false);
        apply(stage.getX(), monitor.getMinY() + 24, stage.getWidth(), stage.getHeight(), monitor);
        persist();
    }

    void dockTo(Rectangle2D target) {
        monitor = target;
        docked.set(true);
        apply(stage.getX(), monitor.getMinY(), stage.getWidth(), stage.getHeight(), monitor);
        persist();
    }

    public void preset(double width, double height) {
        monitor = screenForWindow();
        apply(stage.getX() + (stage.getWidth() - width) / 2, stage.getY(), width, height, monitor);
        persist();
    }

    public void fill() {
        monitor = screenForWindow();
        apply(monitor.getMinX(), monitor.getMinY(), monitor.getWidth(), monitor.getHeight(), monitor);
        persist();
    }

    public void restoreManualSize() {
        preset(manualWidth, manualHeight);
    }

    public void ensureVisible() {
        monitor = screenForWindow();
        apply(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight(), monitor);
    }

    void apply(double x, double y, double width, double height, Rectangle2D bounds) {
        width = Math.clamp(width, Math.min(420, bounds.getWidth()), bounds.getWidth());
        height = Math.clamp(height, Math.min(320, bounds.getHeight()), bounds.getHeight());
        stage.setWidth(width);
        stage.setHeight(height);
        stage.setX(isDocked() ? bounds.getMinX() + (bounds.getWidth() - width) / 2 : Math.clamp(x, bounds.getMinX(),
                bounds.getMaxX() - width));
        stage.setY(isDocked() ? bounds.getMinY() : Math.clamp(y, bounds.getMinY(), bounds.getMaxY() - height));
    }

    Rectangle2D screenForWindow() {
        return Screen.getScreensForRectangle(stage.getX(), stage.getY(), Math.max(1, stage.getWidth()),
                        Math.max(1, stage.getHeight())).stream()
                .max((a, b) -> Double.compare(overlap(a.getVisualBounds()), overlap(b.getVisualBounds())))
                .orElse(Screen.getPrimary()).getVisualBounds();
    }

    private double overlap(Rectangle2D b) {
        return Math.max(0,
                Math.min(stage.getX() + stage.getWidth(), b.getMaxX()) - Math.max(stage.getX(), b.getMinX())) *
                Math.max(0,
                        Math.min(stage.getY() + stage.getHeight(), b.getMaxY()) - Math.max(stage.getY(), b.getMinY()));
    }

    static Rectangle2D screenAt(double x, double y) {
        return Screen.getScreensForRectangle(x, y, 1, 1).stream().findFirst().orElse(Screen.getPrimary())
                .getVisualBounds();
    }

    public void persist() {
        preferences.setWindowPlacement(
                new WindowPlacement(isDocked(), stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight(),
                        manualWidth, manualHeight, monitor.getMinX(), monitor.getMinY()));
    }

    private static boolean finite(double... values) {
        for (double value : values) {
            if (!Double.isFinite(value)) {
                return false;
            }
        }

        return true;
    }
}
