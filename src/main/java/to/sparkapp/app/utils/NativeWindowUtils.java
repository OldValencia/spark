package to.sparkapp.app.utils;

import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.awt.*;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class NativeWindowUtils {

    public static volatile double cachedWebviewHeight = 0;

    private static Function msgSend;
    private static Pointer selSetFrameOrigin;
    private static Pointer selOrderOut;
    private static Pointer selMakeKeyAndOrderFront;
    private static boolean macInitialized = false;

    // Win32 style / SetWindowPos constants
    private static final int GWL_STYLE = -16;
    private static final int WS_POPUP = 0x80000000;
    private static final int WS_CHILD = 0x40000000;
    private static final int WS_CAPTION = 0x00C00000;
    private static final int WS_THICKFRAME = 0x00040000;
    private static final int WS_BORDER = 0x00800000;

    private static final int SWP_NOSIZE = 0x0001;
    private static final int SWP_NOMOVE = 0x0002;
    private static final int SWP_NOZORDER = 0x0004;
    private static final int SWP_NOACTIVATE = 0x0010;
    /**
     * Forces a WM_NCCALCSIZE so that the window's client area is recalculated
     * after a SetWindowLong style change.  Without this the new style may not
     * take effect until the next spontaneous repaint, which made re-parenting
     * unreliable in practice.
     */
    private static final int SWP_FRAMECHANGED = 0x0020;

    static {
        if (SystemUtils.isMac()) {
            try {
                var objcLib = NativeLibrary.getInstance("objc");
                msgSend = objcLib.getFunction("objc_msgSend");
                var selReg = objcLib.getFunction("sel_registerName");

                selSetFrameOrigin = (Pointer) selReg.invoke(Pointer.class,
                        new Object[]{"setFrameOrigin:"});
                selOrderOut = (Pointer) selReg.invoke(Pointer.class,
                        new Object[]{"orderOut:"});
                selMakeKeyAndOrderFront = (Pointer) selReg.invoke(Pointer.class,
                        new Object[]{"makeKeyAndOrderFront:"});

                macInitialized = true;
                log.debug("macOS ObjC bridge initialised");
            } catch (Exception e) {
                log.warn("Could not initialise macOS ObjC bridge", e);
            }
        }
    }

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

    /**
     * Re-parents the webview child window under the given JavaFX HWND.
     *
     * <p>The correct sequence is:
     * <ol>
     *   <li>Strip popup/decoration styles, add WS_CHILD via SetWindowLong</li>
     *   <li>Call SetParent</li>
     *   <li>Issue SWP_FRAMECHANGED so Win32 immediately recalculates the
     *       client area — without this the style change can silently be deferred
     *       and the webview may not render inside the parent.</li>
     * </ol>
     */
    public static void setParent(long childHandle, long parentHandle) {
        if (!SystemUtils.isWindows() || childHandle == 0 || parentHandle == 0) {
            return;
        }
        try {
            var child = new WinDef.HWND(Pointer.createConstant(childHandle));
            var parent = new WinDef.HWND(Pointer.createConstant(parentHandle));

            int style = User32.INSTANCE.GetWindowLong(child, GWL_STYLE);
            style = (style & ~(WS_POPUP | WS_CAPTION | WS_THICKFRAME | WS_BORDER)) | WS_CHILD;
            User32.INSTANCE.SetWindowLong(child, GWL_STYLE, style);

            User32.INSTANCE.SetParent(child, parent);

            // Flush the style change into the window's non-client area immediately.
            User32.INSTANCE.SetWindowPos(child, null, 0, 0, 0, 0,
                    SWP_NOSIZE | SWP_NOMOVE | SWP_NOZORDER | SWP_NOACTIVATE | SWP_FRAMECHANGED);

            log.debug("Webview parented successfully");
        } catch (Exception e) {
            log.warn("SetParent failed", e);
        }
    }

    /**
     * Removes the webview window from its parent (used only in edge cases;
     * normal hide-to-tray no longer calls this).
     *
     * <p>Restores the WS_POPUP style so the window is a valid top-level window
     * after the call — the original code left WS_CHILD set, which caused
     * rendering artefacts and made re-parenting unreliable.
     */
    public static void unparent(long childHandle) {
        if (!SystemUtils.isWindows() || childHandle == 0) return;
        try {
            var child = new WinDef.HWND(Pointer.createConstant(childHandle));

            int style = User32.INSTANCE.GetWindowLong(child, GWL_STYLE);
            style = (style & ~WS_CHILD) | WS_POPUP;
            User32.INSTANCE.SetWindowLong(child, GWL_STYLE, style);

            User32.INSTANCE.SetParent(child, null);

            User32.INSTANCE.SetWindowPos(child, null, 0, 0, 0, 0,
                    SWP_NOSIZE | SWP_NOMOVE | SWP_NOZORDER | SWP_NOACTIVATE | SWP_FRAMECHANGED);

            log.debug("Webview unparented");
        } catch (Exception e) {
            log.warn("Unparent failed", e);
        }
    }

    public static void setBounds(long windowHandle, int x, int y, int width, int height) {
        if (windowHandle == 0) return;
        if (SystemUtils.isWindows()) {
            setBoundsWindows(windowHandle, x, y, width, height);
        } else if (SystemUtils.isMac()) {
            setPositionMac(windowHandle, x, y);
        }
    }

    public static void setVisible(long windowHandle, boolean visible) {
        if (windowHandle == 0) return;
        if (SystemUtils.isWindows()) {
            setVisibleWindows(windowHandle, visible);
        } else if (SystemUtils.isMac()) {
            setVisibleMac(windowHandle, visible);
        }
    }

    private static void setBoundsWindows(long handle, int x, int y, int width, int height) {
        try {
            var hwnd = new WinDef.HWND(Pointer.createConstant(handle));
            User32.INSTANCE.SetWindowPos(hwnd, null, x, y, width, height, SWP_NOZORDER | SWP_NOACTIVATE);
        } catch (Exception e) {
            log.warn("SetWindowPos failed", e);
        }
    }

    private static void setVisibleWindows(long handle, boolean visible) {
        try {
            var hwnd = new WinDef.HWND(Pointer.createConstant(handle));
            User32.INSTANCE.ShowWindow(hwnd, visible ? WinUser.SW_SHOWNOACTIVATE : WinUser.SW_HIDE);
        } catch (Exception e) {
            log.warn("ShowWindow failed", e);
        }
    }

    private static void setPositionMac(long handle, int javaX, int javaY) {
        if (!macInitialized) return;
        try {
            var nsWindow = Pointer.createConstant(handle);
            var screenH = (int) GraphicsEnvironment
                    .getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice()
                    .getDefaultConfiguration()
                    .getBounds()
                    .getHeight();

            var macY = screenH - javaY - cachedWebviewHeight;
            msgSend.invoke(Void.class, new Object[]{nsWindow, selSetFrameOrigin, (double) javaX, macY});
        } catch (Exception ignored) {
        }
    }

    private static void setVisibleMac(long handle, boolean visible) {
        if (!macInitialized) return;
        try {
            var nsWindow = Pointer.createConstant(handle);
            if (visible) {
                msgSend.invoke(Void.class, new Object[]{nsWindow, selMakeKeyAndOrderFront, null});
            } else {
                msgSend.invoke(Void.class, new Object[]{nsWindow, selOrderOut, null});
            }
        } catch (Exception ignored) {
        }
    }
}
