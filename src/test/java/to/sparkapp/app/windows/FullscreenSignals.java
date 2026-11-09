package to.sparkapp.app.windows;

import java.nio.file.Path;
import java.util.Set;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.POINT;
import com.sun.jna.platform.win32.WinDef.RECT;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.platform.win32.WinUser.MONITORINFO;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

/**
 * Two independent signals that an app fills the monitor under the cursor: the shell's notification
 * state and the geometry of the foreground window.
 */
record FullscreenSignals(String notificationState, String process, String windowClass, String windowRect,
                         String monitorRect, boolean maximized, boolean caption, boolean coversMonitor,
                         boolean cursorMonitor, boolean ignoredWindow) {
    private static final String[] STATES = {"?", "NOT_PRESENT", "BUSY", "D3D_FULL_SCREEN", "PRESENTATION_MODE",
            "ACCEPTS_NOTIFICATIONS", "QUIET_TIME", "APP"};
    private static final Set<String> SHELL_CLASSES = Set.of("Progman", "WorkerW", "Shell_TrayWnd");
    /** Start, Search and the action center briefly fill the monitor while they open. */
    private static final Set<String> SHELL_PROCESSES = Set.of("SearchHost.exe", "StartMenuExperienceHost.exe",
            "ShellExperienceHost.exe");
    private static final int WS_MAXIMIZE = 0x01000000;
    private static final int WS_CAPTION = 0x00C00000;

    private interface Shell extends StdCallLibrary {
        Shell INSTANCE = Native.load("shell32", Shell.class, W32APIOptions.DEFAULT_OPTIONS);

        int SHQueryUserNotificationState(IntByReference state);
    }

    /** The shell reports an exclusive game, a fullscreen app or a presentation. */
    boolean shellFullscreen() {
        return Set.of("BUSY", "D3D_FULL_SCREEN", "PRESENTATION_MODE").contains(notificationState);
    }

    /**
     * A foreground window that is not Spark or the shell fills the cursor's monitor. Firefox and Java apps keep
     * the maximized style in fullscreen, so only a maximized window with a title bar is a normal window.
     */
    boolean geometryFullscreen() {
        return coversMonitor && cursorMonitor && !(maximized && caption) && !ignoredWindow;
    }

    static FullscreenSignals read() {
        var cursor = new POINT();
        User32.INSTANCE.GetCursorPos(cursor);
        var cursorMonitor = User32.INSTANCE.MonitorFromPoint(new POINT.ByValue(cursor.x, cursor.y),
                WinUser.MONITOR_DEFAULTTONEAREST);

        var window = User32.INSTANCE.GetForegroundWindow();
        var windowMonitor = User32.INSTANCE.MonitorFromWindow(window, WinUser.MONITOR_DEFAULTTONEAREST);
        var monitor = new MONITORINFO();
        User32.INSTANCE.GetMonitorInfo(windowMonitor, monitor);

        var rect = new RECT();
        User32.INSTANCE.GetWindowRect(window, rect);
        var style = User32.INSTANCE.GetWindowLong(window, WinUser.GWL_STYLE);
        var windowClass = className(window);
        var process = processName(window);

        return new FullscreenSignals(queryNotificationState(), process, windowClass, rect.toRectangle().toString(),
                monitor.rcMonitor.toRectangle().toString(), (style & WS_MAXIMIZE) != 0, (style & WS_CAPTION) != 0,
                covers(rect, monitor.rcMonitor), cursorMonitor.equals(windowMonitor),
                SHELL_CLASSES.contains(windowClass) || SHELL_PROCESSES.contains(process) ||
                        process.equals(ownProcessName()));
    }

    private static String queryNotificationState() {
        var state = new IntByReference();
        var result = Shell.INSTANCE.SHQueryUserNotificationState(state);

        if (result < 0 || state.getValue() < 0 || state.getValue() >= STATES.length) {
            return "error 0x" + Integer.toHexString(result);
        }

        return STATES[state.getValue()];
    }

    private static boolean covers(RECT window, RECT monitor) {
        return window.left <= monitor.left && window.top <= monitor.top && window.right >= monitor.right &&
                window.bottom >= monitor.bottom;
    }

    private static String className(HWND window) {
        var buffer = new char[256];
        var length = User32.INSTANCE.GetClassName(window, buffer, buffer.length);

        return new String(buffer, 0, Math.max(length, 0));
    }

    private static String processName(HWND window) {
        if (window == null || Pointer.nativeValue(window.getPointer()) == 0) {
            return "(none)";
        }

        var process = new IntByReference();
        User32.INSTANCE.GetWindowThreadProcessId(window, process);

        return ProcessHandle.of(Integer.toUnsignedLong(process.getValue()))
                .flatMap(handle -> handle.info().command())
                .map(command -> Path.of(command).getFileName().toString())
                .orElse("pid " + process.getValue());
    }

    private static String ownProcessName() {
        return ProcessHandle.current().info().command()
                .map(command -> Path.of(command).getFileName().toString())
                .orElse("");
    }
}
