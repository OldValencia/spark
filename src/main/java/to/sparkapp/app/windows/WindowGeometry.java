package to.sparkapp.app.windows;

import java.util.function.Consumer;

import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.config.AppPreferences.WindowPlacement;
import to.sparkapp.app.config.SiteWindowSize;
import to.sparkapp.app.config.WindowSizePreset.Slot;

final class WindowGeometry {
    private final Stage stage;
    private final AppPreferences preferences;
    final ReadOnlyBooleanWrapper docked = new ReadOnlyBooleanWrapper(false);
    private double manualWidth = 860;
    private double manualHeight = 700;
    /** The size of sites without one of their own; the window may show another site's size meanwhile. */
    private double sharedWidth;
    private double sharedHeight;
    private Consumer<SiteWindowSize> userResized = size -> {
    };
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

        rememberSharedSize();
    }

    /** Reports each size the user chooses: a preset, the whole screen, or a size set by hand. */
    void setOnUserResized(Consumer<SiteWindowSize> listener) {
        userResized = listener;
    }

    void rememberSharedSize() {
        sharedWidth = stage.getWidth();
        sharedHeight = stage.getHeight();
    }

    double sharedWidth() {
        return sharedWidth;
    }

    double sharedHeight() {
        return sharedHeight;
    }

    boolean isDocked() {
        return docked.get();
    }

    void rememberManualSize() {
        manualWidth = stage.getWidth();
        manualHeight = stage.getHeight();
        userResized.accept(SiteWindowSize.custom(manualWidth, manualHeight));
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
        resizeAroundCenter(width, height);
        userResized.accept(SiteWindowSize.custom(width, height));
        persist();
    }

    public void preset(Slot slot) {
        var preset = preferences.getWindowSizePreset(slot);
        resizeAroundCenter(preset.width(), preset.height());
        userResized.accept(SiteWindowSize.ofPreset(slot));
        persist();
    }

    private void resizeAroundCenter(double width, double height) {
        monitor = screenForWindow();
        apply(stage.getX() + (stage.getWidth() - width) / 2, stage.getY(), width, height, monitor);
    }

    public void fill() {
        fillScreen();
        userResized.accept(SiteWindowSize.filling());
        persist();
    }

    void fillScreen() {
        monitor = screenForWindow();
        apply(monitor.getMinX(), monitor.getMinY(), monitor.getWidth(), monitor.getHeight(), monitor);
    }

    boolean fillsScreen() {
        return stage.getWidth() == monitor.getWidth() && stage.getHeight() == monitor.getHeight();
    }

    /** Changes the size while the top left corner stays in place, so the tabs stay under the pointer. */
    void resizeFromCorner(double x, double y, double width, double height) {
        monitor = screenForWindow();
        apply(x, y, width, height, monitor);
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
        return WindowScreens.screenFor(stage);
    }

    public void persist() {
        persist(stage.getY());
    }

    void persist(double restingY) {
        preferences.setWindowPlacement(
                new WindowPlacement(isDocked(), stage.getX(), restingY, sharedWidth, sharedHeight, manualWidth,
                        manualHeight, monitor.getMinX(), monitor.getMinY()));
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
