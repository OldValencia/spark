package to.sparkapp.app.utils;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class NativeWindowUtils {

    public static void makeNonActivatingToolWindow(String title) {
        WindowsWindowStyle.makeNonActivatingToolWindow(title);
    }

    public static void setParent(long child, long parent) {
        WindowsWindowStyle.setParent(child, parent);
    }

    public static void unparent(long child) {
        WindowsWindowStyle.unparent(child);
    }

    public static long foregroundWindow() {
        if (!SystemUtils.isWindows()) {
            return 0;
        }

        var window = User32.INSTANCE.GetForegroundWindow();

        return window == null ? 0 : Pointer.nativeValue(window.getPointer());
    }

    public static boolean isSparkForeground() {
        if (!SystemUtils.isWindows()) {
            return false;
        }

        return WindowsInteraction.ownsForeground();
    }

    public static boolean isSparkInteractionActive() {
        return SystemUtils.isWindows() && (WindowsInteraction.ownsForeground() || WindowsInteraction.hasModalWindow());
    }

    public static void restoreForeground(long handle) {
        if (!SystemUtils.isWindows() || handle == 0) {
            return;
        }

        var window = new WinDef.HWND(new Pointer(handle));

        if (User32.INSTANCE.IsWindow(window)) {
            User32.INSTANCE.SetForegroundWindow(window);
        }
    }

    /**
     * GetDpiForWindow is not exposed by JNA's User32 mapping, so it is bound separately. It is only
     * available on Windows 10 1607+; older systems fall back to the default 96 DPI.
     */
    private interface User32Dpi extends StdCallLibrary {
        User32Dpi INSTANCE = Native.load("user32", User32Dpi.class, W32APIOptions.DEFAULT_OPTIONS);

        int GetDpiForWindow(WinDef.HWND hwnd);
    }

    public static final int DEFAULT_DPI = 96;

    public static long getJavaFXWindowHandle(String windowTitle) {
        if (!SystemUtils.isWindows() || windowTitle == null || windowTitle.isEmpty()) {
            return 0L;
        }

        try {
            var hwnd = User32.INSTANCE.FindWindow(null, windowTitle);

            if (hwnd != null) {
                return Pointer.nativeValue(hwnd.getPointer());
            }

            return 0L;
        } catch (Exception e) {
            log.warn("Error getting HWND for JavaFX window", e);

            return 0L;
        }
    }

    public static void setBounds(long windowHandle, int x, int y, int width, int height) {
        if (windowHandle == 0) {
            return;
        }

        setBoundsWindows(windowHandle, x, y, width, height);
    }

    public static void setVisible(long windowHandle, boolean visible) {
        if (windowHandle == 0) {
            return;
        }

        setVisibleWindows(windowHandle, visible);
    }

    private static void setBoundsWindows(long handle, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }

        try {
            var hwnd = new WinDef.HWND(new Pointer(handle));
            User32.INSTANCE.SetWindowPos(hwnd, null, x, y, width, height,
                    WinUser.SWP_NOZORDER | WinUser.SWP_NOACTIVATE);
        } catch (Exception e) {
            log.warn("SetWindowPos failed", e);
        }
    }

    private static void setVisibleWindows(long handle, boolean visible) {
        try {
            var hwnd = new WinDef.HWND(new Pointer(handle));
            User32.INSTANCE.ShowWindow(hwnd, visible ? WinUser.SW_SHOWNOACTIVATE : WinUser.SW_HIDE);
        } catch (Exception e) {
            log.warn("ShowWindow failed", e);
        }
    }

    /**
     * Returns the client area size of a window in physical pixels, or {@code null} if the handle is
     * not a live window.
     */
    public static int[] getClientSize(long windowHandle) {
        if (!SystemUtils.isWindows() || windowHandle == 0) {
            return null;
        }

        try {
            var hwnd = new WinDef.HWND(new Pointer(windowHandle));

            if (!User32.INSTANCE.IsWindow(hwnd)) {
                return null;
            }

            var rect = new WinDef.RECT();

            if (!User32.INSTANCE.GetClientRect(hwnd, rect)) {
                return null;
            }

            return new int[]{rect.right - rect.left, rect.bottom - rect.top};
        } catch (Throwable e) {
            log.debug("GetClientRect failed", e);

            return null;
        }
    }

    /**
     * DPI of the monitor the window currently lives on (96 = 100% scale).
     */
    public static int getWindowDpi(long windowHandle) {
        if (!SystemUtils.isWindows() || windowHandle == 0) {
            return DEFAULT_DPI;
        }

        try {
            var dpi = User32Dpi.INSTANCE.GetDpiForWindow(new WinDef.HWND(new Pointer(windowHandle)));

            return dpi > 0 ? dpi : DEFAULT_DPI;
        } catch (Throwable e) {
            return DEFAULT_DPI;
        }
    }

    /**
     * True when the given window is already a child of the given parent, so the (expensive) style
     * rewrite in {@link #setParent} can be skipped.
     */
    public static boolean isChildOf(long childHandle, long parentHandle) {
        if (!SystemUtils.isWindows() || childHandle == 0 || parentHandle == 0) {
            return false;
        }

        try {
            var current = User32.INSTANCE.GetParent(new WinDef.HWND(new Pointer(childHandle)));

            return current != null && Pointer.nativeValue(current.getPointer()) == parentHandle;
        } catch (Throwable e) {
            return false;
        }
    }
}
