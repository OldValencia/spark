package to.sparkapp.app.readme;

import java.awt.AWTException;
import java.awt.MouseInfo;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javafx.geometry.Point2D;

/** Moves the real pointer like a person would, so the recording shows where each action happens. */
final class ReadmeMouse {
    private static final int STEPS_PER_MOVE = 28;
    private static final int MOVE_MILLIS = 420;
    private static final int TYPING_PAUSE_MILLIS = 60;
    private static final int KEY_GAP_MILLIS = 30;
    private static final int KEY_HOLD_MILLIS = 90;

    private final Robot robot;

    ReadmeMouse() throws AWTException {
        robot = new Robot();
        robot.setAutoWaitForIdle(false);
    }

    void moveTo(Point2D target) {
        var start = MouseInfo.getPointerInfo().getLocation();
        for (var step = 1; step <= STEPS_PER_MOVE; step++) {
            var progress = easeInOut(step / (double) STEPS_PER_MOVE);
            var x = start.x + (target.getX() - start.x) * progress;
            var y = start.y + (target.getY() - start.y) * progress;
            robot.mouseMove((int) Math.round(x), (int) Math.round(y));
            ReadmeFx.pause(MOVE_MILLIS / STEPS_PER_MOVE);
        }
    }

    void click(Point2D target) {
        moveTo(target);
        ReadmeFx.pause(120);
        press();
    }

    void rightClick(Point2D target) {
        moveTo(target);
        ReadmeFx.pause(120);
        robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
        ReadmeFx.pause(60);
        robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
    }

    void doubleClick(Point2D target) {
        moveTo(target);
        ReadmeFx.pause(120);
        press();
        ReadmeFx.pause(90);
        press();
    }

    void scroll(int notches) {
        for (var notch = 0; notch < Math.abs(notches); notch++) {
            robot.mouseWheel(Integer.signum(notches));
            ReadmeFx.pause(140);
        }
    }

    /**
     * Enters text through the clipboard, so the result does not depend on the keyboard layout.
     * The previous clipboard text is restored afterwards.
     */
    void type(String text) {
        var clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        String previous = null;
        try {
            if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                previous = (String) clipboard.getData(DataFlavor.stringFlavor);
            }
        } catch (Exception unavailable) {
            previous = null;
        }

        for (var character : text.split("")) {
            clipboard.setContents(new StringSelection(character), null);
            key(KeyEvent.VK_CONTROL, KeyEvent.VK_V);
            ReadmeFx.pause(TYPING_PAUSE_MILLIS);
        }
        ReadmeFx.pause(200);
        if (previous != null) {
            clipboard.setContents(new StringSelection(previous), null);
        }
    }

    /**
     * Presses keys together and holds them briefly, as a person does. A page's shortcuts are read while the keys
     * are down: Spark checks Ctrl and Shift on the keyboard when the browser reports the key.
     */
    void key(int... codes) {
        for (var code : codes) {
            robot.keyPress(code);
            ReadmeFx.pause(KEY_GAP_MILLIS);
        }
        ReadmeFx.pause(KEY_HOLD_MILLIS);
        for (var index = codes.length - 1; index >= 0; index--) {
            robot.keyRelease(codes[index]);
        }
        ReadmeFx.pause(120);
    }

    private void press() {
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        ReadmeFx.pause(60);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    private static double easeInOut(double value) {
        return value < 0.5 ? 2 * value * value : 1 - Math.pow(-2 * value + 2, 2) / 2;
    }
}
