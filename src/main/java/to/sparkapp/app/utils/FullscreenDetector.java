package to.sparkapp.app.utils;

import java.nio.file.Path;
import java.util.function.Supplier;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.POINT;
import com.sun.jna.platform.win32.WinDef.RECT;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.platform.win32.WinUser.HMONITOR;
import com.sun.jna.platform.win32.WinUser.MONITORINFO;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Detects a game, video or presentation that fills the monitor under the pointer.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FullscreenDetector {
    private static final int QUNS_RUNNING_D3D_FULL_SCREEN = 3;

    private interface Shell extends StdCallLibrary {
        Shell INSTANCE = Native.load("shell32", Shell.class, W32APIOptions.DEFAULT_OPTIONS);

        int SHQueryUserNotificationState(IntByReference state);
    }

    /**
     * Whether the foreground window fills the pointer's monitor, or a game holds the display in exclusive
     * fullscreen. The shell's notification state is not used for other cases: it is system-wide and lags about
     * a second behind switching apps.
     */
    public static boolean isFullscreenUnderPointer() {
        return SystemUtils.isWindows() && isFullscreenOn(FullscreenDetector::pointerMonitor);
    }

    /** Whether a fullscreen app fills the monitor with this handle, as returned by {@link #monitorOf(long)}. */
    public static boolean isFullscreenOnMonitor(long monitorHandle) {
        return SystemUtils.isWindows() && monitorHandle != 0 &&
                isFullscreenOn(() -> new HMONITOR(new Pointer(monitorHandle)));
    }

    /** The handle of the monitor that holds most of a window, or 0. */
    public static long monitorOf(long windowHandle) {
        if (!SystemUtils.isWindows() || windowHandle == 0) {
            return 0;
        }

        var monitor = User32.INSTANCE.MonitorFromWindow(new HWND(new Pointer(windowHandle)),
                WinUser.MONITOR_DEFAULTTONULL);

        return monitor == null ? 0 : Pointer.nativeValue(monitor.getPointer());
    }

    private static boolean isFullscreenOn(Supplier<HMONITOR> target) {
        try {
            return foregroundFills(target.get()) || isExclusiveFullscreen();
        } catch (RuntimeException | UnsatisfiedLinkError error) {
            log.debug("Fullscreen detection failed", error);

            return false;
        }
    }

    private static boolean foregroundFills(HMONITOR target) {
        var window = User32.INSTANCE.GetForegroundWindow();

        if (window == null) {
            return false;
        }

        var monitor = User32.INSTANCE.MonitorFromWindow(window, WinUser.MONITOR_DEFAULTTONEAREST);

        if (!monitor.equals(target)) {
            return false;
        }

        var monitorInfo = new MONITORINFO();
        var bounds = new RECT();

        if (!User32.INSTANCE.GetMonitorInfo(monitor, monitorInfo).booleanValue() ||
                !User32.INSTANCE.GetWindowRect(window, bounds)) {
            return false;
        }

        var style = User32.INSTANCE.GetWindowLong(window, WinUser.GWL_STYLE);

        return FullscreenRule.fillsMonitor(bounds, monitorInfo.rcMonitor, style) && !isShellOrSpark(window);
    }

    private static HMONITOR pointerMonitor() {
        var pointer = new POINT();
        User32.INSTANCE.GetCursorPos(pointer);

        return User32.INSTANCE.MonitorFromPoint(new POINT.ByValue(pointer.x, pointer.y),
                WinUser.MONITOR_DEFAULTTONEAREST);
    }

    private static boolean isShellOrSpark(HWND window) {
        var processId = new IntByReference();
        User32.INSTANCE.GetWindowThreadProcessId(window, processId);
        var pid = Integer.toUnsignedLong(processId.getValue());

        if (pid == ProcessHandle.current().pid()) {
            return true;
        }

        return FullscreenRule.isShellWindow(className(window), processName(pid));
    }

    private static String className(HWND window) {
        var buffer = new char[256];
        var length = User32.INSTANCE.GetClassName(window, buffer, buffer.length);

        return new String(buffer, 0, Math.max(length, 0));
    }

    private static String processName(long pid) {
        return ProcessHandle.of(pid)
                .flatMap(process -> process.info().command())
                .map(command -> Path.of(command).getFileName().toString())
                .orElse("");
    }

    private static boolean isExclusiveFullscreen() {
        var state = new IntByReference();

        return Shell.INSTANCE.SHQueryUserNotificationState(state) >= 0 &&
                state.getValue() == QUNS_RUNNING_D3D_FULL_SCREEN;
    }
}
