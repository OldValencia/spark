package to.sparkapp.app.utils;

import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class WindowsWindowStyle {
    public static void makeNonActivatingToolWindow(String title) {
        if (!SystemUtils.isWindows()) {
            return;
        }

        var handle = NativeWindowUtils.getJavaFXWindowHandle(title);

        if (handle == 0) {
            return;
        }

        var window = new WinDef.HWND(new Pointer(handle));
        boolean visible = User32.INSTANCE.IsWindowVisible(window);
        // Windows requires hide/change/show to remove an existing taskbar button.

        if (visible) {
            User32.INSTANCE.ShowWindow(window, WinUser.SW_HIDE);
        }

        int style = getWindowLong(window, GWL_EXSTYLE);
        setWindowLong(window, GWL_EXSTYLE, (style & ~WS_EX_APPWINDOW) | WS_EX_NOACTIVATE | WS_EX_TOOLWINDOW);
        User32.INSTANCE.SetWindowPos(window, null, 0, 0, 0, 0,
                SWP_NOSIZE | SWP_NOMOVE | SWP_NOZORDER | SWP_NOACTIVATE | SWP_FRAMECHANGED);

        if (visible) {
            User32.INSTANCE.ShowWindow(window, WinUser.SW_SHOWNOACTIVATE);
        }
    }

    // Win32 style / SetWindowPos constants
    private static final int GWL_STYLE = -16;
    private static final int GWL_EXSTYLE = -20;

    private static final int WS_POPUP = 0x80000000;
    private static final int WS_CHILD = 0x40000000;
    private static final int WS_CLIPSIBLINGS = 0x04000000;
    private static final int WS_CLIPCHILDREN = 0x02000000;
    private static final int WS_CAPTION = 0x00C00000;
    private static final int WS_BORDER = 0x00800000;
    private static final int WS_DLGFRAME = 0x00400000;
    private static final int WS_SYSMENU = 0x00080000;
    private static final int WS_THICKFRAME = 0x00040000;
    private static final int WS_MINIMIZEBOX = 0x00020000;
    private static final int WS_MAXIMIZEBOX = 0x00010000;

    private static final int WS_EX_DLGMODALFRAME = 0x00000001;
    private static final int WS_EX_WINDOWEDGE = 0x00000100;
    private static final int WS_EX_CLIENTEDGE = 0x00000200;
    private static final int WS_EX_STATICEDGE = 0x00020000;
    private static final int WS_EX_APPWINDOW = 0x00040000;
    private static final int WS_EX_TOOLWINDOW = 0x00000080;
    private static final int WS_EX_NOACTIVATE = 0x08000000;

    private static final int SWP_NOSIZE = 0x0001;
    private static final int SWP_NOMOVE = 0x0002;
    private static final int SWP_NOZORDER = 0x0004;
    private static final int SWP_NOACTIVATE = 0x0010;
    private static final int SWP_FRAMECHANGED = 0x0020;

    private static int getWindowLong(WinDef.HWND hwnd, int index) {
        if (Platform.is64Bit()) {
            return User32.INSTANCE.GetWindowLongPtr(hwnd, index).intValue();
        } else {
            return User32.INSTANCE.GetWindowLong(hwnd, index);
        }
    }

    private static void setWindowLong(WinDef.HWND hwnd, int index, int value) {
        if (Platform.is64Bit()) {
            User32.INSTANCE.SetWindowLongPtr(hwnd, index, new Pointer(value));
        } else {
            User32.INSTANCE.SetWindowLong(hwnd, index, value);
        }
    }

    public static void setParent(long childHandle, long parentHandle) {
        if (!SystemUtils.isWindows() || childHandle == 0 || parentHandle == 0) {
            return;
        }

        try {
            var child = new WinDef.HWND(new Pointer(childHandle));
            var parent = new WinDef.HWND(new Pointer(parentHandle));

            int style = getWindowLong(child, GWL_STYLE);
            style = (style &
                    ~(WS_POPUP | WS_CAPTION | WS_THICKFRAME | WS_BORDER | WS_SYSMENU | WS_MINIMIZEBOX | WS_MAXIMIZEBOX |
                            WS_DLGFRAME)) | WS_CHILD | WS_CLIPSIBLINGS | WS_CLIPCHILDREN;
            setWindowLong(child, GWL_STYLE, style);

            int exStyle = getWindowLong(child, GWL_EXSTYLE);
            exStyle = exStyle &
                    ~(WS_EX_DLGMODALFRAME | WS_EX_WINDOWEDGE | WS_EX_CLIENTEDGE | WS_EX_STATICEDGE | WS_EX_APPWINDOW);
            setWindowLong(child, GWL_EXSTYLE, exStyle);

            User32.INSTANCE.SetParent(child, parent);

            // Flush the style change into the window's non-client area immediately.
            User32.INSTANCE.SetWindowPos(child, null, 0, 0, 0, 0,
                    SWP_NOSIZE | SWP_NOMOVE | SWP_NOZORDER | SWP_NOACTIVATE | SWP_FRAMECHANGED);

            log.debug("Webview parented successfully");
        } catch (Throwable e) {
            log.warn("SetParent failed", e);
        }
    }

    /**
     * Detaches a hidden browser before JavaFX can destroy the host HWND. The browser keeps its
     * viewport and live page until it is reattached.
     */
    public static void unparent(long childHandle) {
        if (!SystemUtils.isWindows() || childHandle == 0) {
            return;
        }

        try {
            var child = new WinDef.HWND(new Pointer(childHandle));
            int style = getWindowLong(child, GWL_STYLE);

            style = (style & ~WS_CHILD) | WS_POPUP;
            setWindowLong(child, GWL_STYLE, style);

            User32.INSTANCE.SetParent(child, null);

            User32.INSTANCE.SetWindowPos(child, null, 0, 0, 0, 0,
                    SWP_NOSIZE | SWP_NOMOVE | SWP_NOZORDER | SWP_NOACTIVATE | SWP_FRAMECHANGED);

            log.debug("Webview unparented");
        } catch (Throwable e) {
            log.warn("Unparent failed", e);
        }
    }

}
