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
                log.warn("Could not initialise macOS ObjC bridge — window positioning disabled", e);
            }
        }
    }

    public static void setBounds(long windowHandle, int x, int y, int width, int height) {
        if (windowHandle == 0) {
            return;
        }
        if (SystemUtils.isWindows()) {
            setBoundsWindows(windowHandle, x, y, width, height);
        } else if (SystemUtils.isMac()) {
            setPositionMac(windowHandle, x, y);
        }
    }

    public static void setVisible(long windowHandle, boolean visible) {
        if (windowHandle == 0) {
            return;
        }
        if (SystemUtils.isWindows()) {
            setVisibleWindows(windowHandle, visible);
        } else if (SystemUtils.isMac()) {
            setVisibleMac(windowHandle, visible);
        }
    }

    private static void setBoundsWindows(long handle, int x, int y, int width, int height) {
        try {
            var hwnd = new WinDef.HWND(Pointer.createConstant(handle));
            // SWP_NOZORDER | SWP_NOACTIVATE
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
        if (!macInitialized) {
            return;
        }
        try {
            var nsWindow = Pointer.createConstant(handle);

            var screenH = (int) GraphicsEnvironment
                    .getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice()
                    .getDefaultConfiguration()
                    .getBounds()
                    .getHeight();

            // macOS Y-axis is flipped: origin is bottom-left of screen
            var macY = screenH - javaY - cachedWebviewHeight;

            msgSend.invoke(Void.class, new Object[]{nsWindow, selSetFrameOrigin, (double) javaX, macY});
        } catch (Exception e) {
            log.warn("NSWindow setFrameOrigin: failed", e);
        }
    }

    private static void setVisibleMac(long handle, boolean visible) {
        if (!macInitialized) {
            return;
        }
        try {
            var nsWindow = Pointer.createConstant(handle);
            if (visible) {
                msgSend.invoke(Void.class, new Object[]{nsWindow, selMakeKeyAndOrderFront, null});
            } else {
                msgSend.invoke(Void.class, new Object[]{nsWindow, selOrderOut, null});
            }
        } catch (Exception e) {
            log.warn("NSWindow show/hide failed", e);
        }
    }
}
