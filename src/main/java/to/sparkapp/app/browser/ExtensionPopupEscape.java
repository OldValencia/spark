package to.sparkapp.app.browser;

import static to.sparkapp.app.browser.ComCalls.call;
import static to.sparkapp.app.browser.ComCalls.check;

import com.sun.jna.ptr.IntByReference;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Escape pressed inside the extension's document dismisses the popup, as it does in a browser toolbar. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ExtensionPopupEscape {
    private static final int CONTROLLER_ADD_ACCELERATOR_KEY_PRESSED = 19;
    private static final int CONTROLLER_REMOVE_ACCELERATOR_KEY_PRESSED = 20;
    private static final int ARGS_VIRTUAL_KEY = 4;
    private static final int ARGS_PUT_HANDLED = 8;
    private static final int VK_ESCAPE = 27;
    private static final String ACCELERATOR_KEY_PRESSED = "b29c7e28-fa79-41a8-8e44-65811c76dcb2";

    static void install(Webview view, Runnable dismiss) {
        var context = view.windows().activeContext();
        context.getEvents().listen(context.getController(), CONTROLLER_ADD_ACCELERATOR_KEY_PRESSED,
                CONTROLLER_REMOVE_ACCELERATOR_KEY_PRESSED, ACCELERATOR_KEY_PRESSED, args -> {
                    var key = new IntByReference();
                    check(call(args, ARGS_VIRTUAL_KEY, key));
                    if (key.getValue() == VK_ESCAPE) {
                        check(call(args, ARGS_PUT_HANDLED, 1));
                        dismiss.run();
                    }
                });
    }
}
