package io.loom.app.utils;

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

    private static final int GWL_STYLE = -16;
    private static final int WS_POPUP = 0x80000000;
    private static final int WS_CHILD = 0x40000000;

    private static final int WS_CAPTION = 0x00C00000;
    private static final int WS_THICKFRAME = 0x00040000;
    private static final int WS_BORDER = 0x00800000;

    static {
        if (SystemUtils.isMac()) {
            try {
                var objcLib = NativeLibrary.getInstance("objc");
                msgSend = objcLib.getFunction("objc_msgSend");
                var selReg = objcLib.getFunction("sel_registerName");

                selSetFrameOrigin = (Pointer) selReg.invoke(Pointer.class, new Object[]{"setFrameOrigin:"});
                selOrderOut = (Pointer) selReg.invoke(Pointer.class, new Object[]{"orderOut:"});
                selMakeKeyAndOrderFront = (Pointer) selReg.invoke(Pointer.class, new Object[]{"makeKeyAndOrderFront:"});

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
            log.debug("Webview parented successfully");
        } catch (Exception e) {
            log.warn("SetParent failed", e);
        }
    }

    public static void unparent(long childHandle) {
        if (!SystemUtils.isWindows() || childHandle == 0) return;
        try {
            var child = new WinDef.HWND(Pointer.createConstant(childHandle));
            User32.INSTANCE.SetParent(child, null);
            log.debug("Webview unparented to survive hide");
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
            User32.INSTANCE.SetWindowPos(hwnd, null, x, y, width, height, 0x0004 | 0x0010);
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
        } catch (Exception ignored) {}
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
        } catch (Exception ignored) {}
    }
}
