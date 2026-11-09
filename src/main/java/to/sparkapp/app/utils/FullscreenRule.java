package to.sparkapp.app.utils;

import java.util.Locale;
import java.util.Set;

import com.sun.jna.platform.win32.WinDef.RECT;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Tells a fullscreen app from an ordinary window by the foreground window's geometry, style and owner.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class FullscreenRule {
    static final int WS_MAXIMIZE = 0x01000000;
    static final int WS_CAPTION = 0x00C00000;

    private static final Set<String> SHELL_CLASSES = Set.of("Progman", "WorkerW", "Shell_TrayWnd",
            "Shell_SecondaryTrayWnd");
    /** Start, Search and the action center briefly fill the monitor while they open. */
    private static final Set<String> SHELL_PROCESSES = Set.of("searchhost.exe", "startmenuexperiencehost.exe",
            "shellexperiencehost.exe");

    /**
     * Whether the window covers the whole monitor, taskbar included. Firefox and Java apps keep the maximized
     * style in fullscreen, so only a maximized window that still has a title bar is an ordinary window.
     */
    static boolean fillsMonitor(RECT window, RECT monitor, int style) {
        var covers = window.left <= monitor.left && window.top <= monitor.top && window.right >= monitor.right &&
                window.bottom >= monitor.bottom;
        var maximizedWithTitleBar = (style & WS_MAXIMIZE) != 0 && (style & WS_CAPTION) == WS_CAPTION;

        return covers && !maximizedWithTitleBar;
    }

    /** The desktop, the taskbar and the shell's own overlays are never fullscreen apps. */
    static boolean isShellWindow(String windowClass, String processName) {
        return SHELL_CLASSES.contains(windowClass) || SHELL_PROCESSES.contains(processName.toLowerCase(Locale.ROOT));
    }
}
