package to.sparkapp.app.browser;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.mouse.NativeMouseEvent;
import com.github.kwhat.jnativehook.mouse.NativeMouseListener;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.DWORD;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.POINT;
import com.sun.jna.platform.win32.WinDef.RECT;
import javafx.application.Platform;
import javafx.stage.Stage;

/** Native focus also accounts for keyboard interaction inside the WebView child. */
final class ExtensionPopupDismissal implements NativeMouseListener, AutoCloseable {
    private static final int GA_ROOT = 2;
    private static final DWORD GW_OWNER = new DWORD(4);
    private static final int MAX_OWNER_DEPTH = 16;
    private static final long ARM_DELAY_NANOS = 500_000_000L;

    private final Stage window;
    private final HWND handle;
    private boolean armed;
    private long armedAt;

    ExtensionPopupDismissal(Stage window, long handle) {
        this.window = window;
        this.handle = new HWND(new Pointer(handle));
        GlobalScreen.addNativeMouseListener(this);
    }

    void arm() {
        armed = true;
        armedAt = System.nanoTime();
    }

    void checkFocus() {
        if (armed && System.nanoTime() - armedAt > ARM_DELAY_NANOS) {
            var foreground = User32.INSTANCE.GetForegroundWindow();
            if (foreground != null && !belongsToPopup(foreground)) {
                window.close();
            }
        }
    }

    private boolean belongsToPopup(HWND candidate) {
        if (candidate == null) {
            return false;
        }
        var root = User32.INSTANCE.GetAncestor(candidate, GA_ROOT);
        for (var depth = 0; root != null && depth < MAX_OWNER_DEPTH; depth++) {
            if (handle.equals(root)) {
                return true;
            }
            root = User32.INSTANCE.GetWindow(root, GW_OWNER);
        }
        return false;
    }

    @Override
    public void nativeMousePressed(NativeMouseEvent event) {
        Platform.runLater(() -> {
            if (!armed || !window.isShowing()) {
                return;
            }
            var point = new POINT();
            var bounds = new RECT();
            if (User32.INSTANCE.GetCursorPos(point) && User32.INSTANCE.GetWindowRect(handle, bounds)
                    && (point.x < bounds.left || point.x >= bounds.right || point.y < bounds.top || point.y >= bounds.bottom)) {
                var foreground = User32.INSTANCE.GetForegroundWindow();
                if (foreground == null || handle.equals(User32.INSTANCE.GetAncestor(foreground, GA_ROOT)) || !belongsToPopup(foreground)) {
                    window.close();
                }
            }
        });
    }

    @Override
    public void close() {
        armed = false;
        GlobalScreen.removeNativeMouseListener(this);
    }
}
