package to.sparkapp.app.browser;

import com.sun.jna.platform.win32.WinDef.RECT;
import lombok.RequiredArgsConstructor;
import to.sparkapp.app.utils.NativeWindowUtils;

import static to.sparkapp.app.browser.ComCalls.call;
import static to.sparkapp.app.browser.ComCalls.check;

@RequiredArgsConstructor
final class ExtensionPopupViewport {
    private static final int CONTROLLER_GET_BOUNDS = 5;
    private static final int CONTROLLER_PUT_BOUNDS = 6;

    private final long parent;
    private final ExtensionPopupDiagnostics diagnostics;

    /** Win32-only check that is safe on any thread; the browser thread is used only when it returns true. */
    boolean needsSynchronization(long browserHandle) {
        var client = NativeWindowUtils.getClientSize(parent);
        if (browserHandle == 0 || client == null || client[0] <= 0 || client[1] <= 0) {
            return false;
        }

        var inset = inset();
        var current = NativeWindowUtils.getClientSize(browserHandle);
        return current == null || current[0] != client[0] - 2 * inset || current[1] != client[1] - 2 * inset;
    }

    void synchronize(Webview view) {
        var client = NativeWindowUtils.getClientSize(parent);
        if (client == null || client[0] <= 0 || client[1] <= 0) {
            return;
        }

        var inset = inset();
        var width = Math.max(1, client[0] - 2 * inset);
        var height = Math.max(1, client[1] - 2 * inset);
        var handle = view.getNativeWindowPointer();
        var current = NativeWindowUtils.getClientSize(handle);
        if (current == null || current[0] != width || current[1] != height) {
            diagnostics.viewport(client, current, width, height, inset);
            NativeWindowUtils.setBounds(handle, inset, inset, width, height);
        }
        synchronizeController(view, width, height);
    }

    private void synchronizeController(Webview view, int width, int height) {
        var controller = view.windows().activeContext().getController();
        var bounds = new RECT();
        check(call(controller, CONTROLLER_GET_BOUNDS, bounds));
        if (bounds.left != 0 || bounds.top != 0 || bounds.right != width || bounds.bottom != height) {
            var updated = new BrowserContext.Bounds();
            updated.right = width;
            updated.bottom = height;
            check(call(controller, CONTROLLER_PUT_BOUNDS, updated));
        }
    }

    private int inset() {
        var dpi = NativeWindowUtils.getWindowDpi(parent);
        return (int) Math.round(ExtensionPopupPlacement.CONTENT_INSET * dpi / NativeWindowUtils.DEFAULT_DPI);
    }
}
