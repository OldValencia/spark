package to.sparkapp.app.browser;

import java.util.function.Consumer;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.ptr.IntByReference;
import lombok.Setter;

import static to.sparkapp.app.browser.ComCalls.call;
import static to.sparkapp.app.browser.ComCalls.check;

/**
 * Takes site shortcuts before the page sees them, including inside frames. Runs on the owning
 * WebView2 thread.
 */
final class BrowserShortcutKeys {
    private static final int CONTROLLER_ADD_ACCELERATOR_KEY_PRESSED = 19;
    private static final int CONTROLLER_REMOVE_ACCELERATOR_KEY_PRESSED = 20;
    private static final String ACCELERATOR_KEY_PRESSED_HANDLER = "b29c7e28-fa79-41a8-8e44-65811c76dcb2";
    private static final int ARGS_GET_KEY_EVENT_KIND = 3;
    private static final int ARGS_GET_VIRTUAL_KEY = 4;
    private static final int ARGS_GET_KEY_EVENT_LPARAM = 5;
    /** Set in a key message when the key was already down: a repeat while the key is held. */
    private static final int LPARAM_WAS_DOWN = 1 << 30;
    private static final int ARGS_PUT_HANDLED = 8;
    private static final int KEY_DOWN = 0;
    private static final int PRESSED = 0x8000;

    @Setter
    private Consumer<SiteShortcut> pressed = shortcut -> {
    };

    void install(BrowserContext context) {
        context.getEvents().listen(context.getController(), CONTROLLER_ADD_ACCELERATOR_KEY_PRESSED,
                CONTROLLER_REMOVE_ACCELERATOR_KEY_PRESSED, ACCELERATOR_KEY_PRESSED_HANDLER, this::keyPressed);
    }

    private void keyPressed(Pointer args) {
        var kind = new IntByReference();
        check(call(args, ARGS_GET_KEY_EVENT_KIND, kind));

        if (kind.getValue() != KEY_DOWN || !isDown(WinUser.VK_CONTROL) || isDown(WinUser.VK_MENU)) {
            return;
        }

        var key = new IntByReference();
        check(call(args, ARGS_GET_VIRTUAL_KEY, key));
        var shortcut = SiteShortcut.fromKey(key.getValue(), isDown(WinUser.VK_SHIFT));

        if (shortcut == null) {
            return;
        }

        // Repeats are kept from the page too, but act only once, so holding the keys cannot open and close a list.
        check(call(args, ARGS_PUT_HANDLED, 1));

        if (!isRepeat(args)) {
            pressed.accept(shortcut);
        }
    }

    private static boolean isRepeat(Pointer args) {
        var lParam = new IntByReference();

        return call(args, ARGS_GET_KEY_EVENT_LPARAM, lParam) >= 0 && (lParam.getValue() & LPARAM_WAS_DOWN) != 0;
    }

    /** The event does not report modifiers, so they are read from the keyboard. */
    private static boolean isDown(int virtualKey) {
        return (User32.INSTANCE.GetAsyncKeyState(virtualKey) & PRESSED) != 0;
    }
}
