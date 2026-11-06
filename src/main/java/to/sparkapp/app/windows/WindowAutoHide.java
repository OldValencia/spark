package to.sparkapp.app.windows;

import java.awt.*;
import java.util.function.BooleanSupplier;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.robot.Robot;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.utils.NativeWindowUtils;
import to.sparkapp.app.utils.SystemUtils;

final class WindowAutoHide {
    private final Stage window;
    private final AppPreferences appPreferences;
    private final WindowController windowController;
    private final BooleanSupplier authentication;
    private final Runnable hideWithoutFocus;
    private final PauseTransition outsideClick = new PauseTransition(Duration.millis(100));
    private volatile boolean windowHidden;
    private long lastShown;
    private long lastHoverSample;
    private Robot pointer;

    WindowAutoHide(Stage window, AppPreferences appPreferences, WindowController windowController, BooleanSupplier authentication, Runnable hideWithoutFocus) {
        this.window = window;
        this.appPreferences = appPreferences;
        this.windowController = windowController;
        this.authentication = authentication;
        this.hideWithoutFocus = hideWithoutFocus;
        windowHidden = !window.isShowing();
        window.showingProperty().addListener((observable, previous, showing) -> windowHidden = !showing);
    }

    void shown() {
        lastShown = System.nanoTime();
    }

    void cancel() {
        outsideClick.stop();
    }

    public void onGlobalMousePressed() {
        if (!appPreferences.isAutoHide()) {
            return;
        }

        Platform.runLater(() -> {
            if (!appPreferences.isAutoHide() || !window.isShowing() || authentication.getAsBoolean() ||
                    System.nanoTime() - lastShown < 250_000_000L) {
                return;
            }

            outsideClick.setOnFinished(e -> {
                boolean ownForeground = SystemUtils.isWindows() ? NativeWindowUtils.isSparkForeground() : Window.getWindows()
                        .stream().anyMatch(Window::isFocused);

                if (window.isShowing() && appPreferences.isAutoHide() && !authentication.getAsBoolean() &&
                        !ownForeground) {
                    hideWithoutFocus.run();
                }
            });
            outsideClick.playFromStart();
        });
    }

    public void onGlobalMouseMoved() {
        if (!windowHidden || (appPreferences.isTopPill() && !appPreferences.isTopEdgeReveal())) {
            return;
        }

        long now = System.nanoTime();

        if (now - lastHoverSample < 40_000_000L) {
            return;
        }

        lastHoverSample = now;
        Platform.runLater(() -> {
            if (window.isShowing() || windowController == null) {
                return;
            }

            if (pointer == null) {
                pointer = new Robot();
            }

            var position = pointer.getMousePosition();
            windowController.handleHiddenPointer(position.getX(), position.getY());
        });
    }

}
