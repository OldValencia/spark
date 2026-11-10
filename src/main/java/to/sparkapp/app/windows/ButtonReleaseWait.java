package to.sparkapp.app.windows;

import java.util.function.BooleanSupplier;

import com.sun.jna.platform.win32.User32;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
import to.sparkapp.app.utils.SystemUtils;

/**
 * Holds an action back while a mouse button is down. A site opened by holding a dragged file over its tab gets its
 * window size only after the drop: resizing the window in the middle of a drag would lose Windows' drag image and move
 * the place the file is dropped on.
 */
final class ButtonReleaseWait {
    private static final int VK_LBUTTON = 0x01;
    private static final int VK_RBUTTON = 0x02;
    private static final int KEY_DOWN = 0x8000;
    private static final Duration CHECK_INTERVAL = Duration.millis(100);

    private final BooleanSupplier buttonDown;
    private final Timeline checks = new Timeline(new KeyFrame(CHECK_INTERVAL, event -> check()));
    private Runnable pending;

    ButtonReleaseWait() {
        this(ButtonReleaseWait::isAnyButtonDown);
    }

    ButtonReleaseWait(BooleanSupplier buttonDown) {
        this.buttonDown = buttonDown;
        checks.setCycleCount(Animation.INDEFINITE);
    }

    /** Runs the action now, or once no mouse button is down. A later action replaces one that is still waiting. */
    void run(Runnable action) {
        if (buttonDown.getAsBoolean()) {
            pending = action;
            checks.play();

            return;
        }

        checks.stop();
        pending = null;
        action.run();
    }

    private void check() {
        if (buttonDown.getAsBoolean()) {
            return;
        }

        checks.stop();
        var action = pending;
        pending = null;

        if (action != null) {
            action.run();
        }
    }

    /** Reads the buttons themselves, so it also sees a drag that started in another application. */
    private static boolean isAnyButtonDown() {
        return SystemUtils.isWindows() && (isDown(VK_LBUTTON) || isDown(VK_RBUTTON));
    }

    private static boolean isDown(int key) {
        return (User32.INSTANCE.GetAsyncKeyState(key) & KEY_DOWN) != 0;
    }
}
