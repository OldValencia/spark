package to.sparkapp.app.windows;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.mouse.NativeMouseEvent;
import com.github.kwhat.jnativehook.mouse.NativeMouseListener;
import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.robot.Robot;
import javafx.stage.Popup;
import javafx.stage.Window;

/**
 * Reports presses on the site's page. The page is a native browser window, so its clicks never reach JavaFX, and
 * Spark's menus would otherwise stay open while the user works in the page.
 *
 * <p>Presses on Spark's own controls are left to JavaFX, which closes its menus itself; acting on those too could
 * close a menu that the same click has just opened.
 */
final class PagePressWatch implements NativeMouseListener, AutoCloseable {
    private final Node page;
    private final Runnable pressed;
    private Robot robot;

    PagePressWatch(Node page, Runnable pressed) {
        this.page = page;
        this.pressed = pressed;
        GlobalScreen.addNativeMouseListener(this);
    }

    @Override
    public void nativeMousePressed(NativeMouseEvent event) {
        Platform.runLater(this::checkPress);
    }

    private void checkPress() {
        var window = page.getScene() == null ? null : page.getScene().getWindow();

        if (window == null || !window.isShowing()) {
            return;
        }

        if (robot == null) {
            robot = new Robot();
        }

        if (isPagePress(robot.getMousePosition())) {
            pressed.run();
        }
    }

    /** Whether a press at this point of the screen lands on the page rather than on a menu shown over it. */
    boolean isPagePress(Point2D pointer) {
        return isOnPage(pointer) && !isOnPopup(pointer);
    }

    private boolean isOnPage(Point2D pointer) {
        var bounds = page.localToScreen(page.getBoundsInLocal());

        return bounds != null && bounds.contains(pointer);
    }

    /** A menu shown over the page is a window of its own; pressing inside it is not a press on the page. */
    private static boolean isOnPopup(Point2D pointer) {
        return Window.getWindows().stream()
                .filter(Popup.class::isInstance)
                .filter(Window::isShowing)
                .anyMatch(popup -> pointer.getX() >= popup.getX() && pointer.getX() < popup.getX() + popup.getWidth() &&
                        pointer.getY() >= popup.getY() && pointer.getY() < popup.getY() + popup.getHeight());
    }

    @Override
    public void close() {
        GlobalScreen.removeNativeMouseListener(this);
    }
}
