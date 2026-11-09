package to.sparkapp.app.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static to.sparkapp.app.utils.FullscreenRule.WS_CAPTION;
import static to.sparkapp.app.utils.FullscreenRule.WS_MAXIMIZE;

import com.sun.jna.platform.win32.WinDef.RECT;
import org.junit.jupiter.api.Test;

/**
 * Window shapes recorded with FullscreenProbe on a 3440 × 1440 monitor.
 */
class FullscreenRuleTest {
    private static final RECT MONITOR = rect(0, 0, 3440, 1440);

    @Test
    void borderlessGameFillsTheMonitor() {
        assertTrue(FullscreenRule.fillsMonitor(rect(0, 0, 3440, 1440), MONITOR, 0));
    }

    @Test
    void browserFullscreenKeepsTheMaximizedStyleWithoutATitleBar() {
        assertTrue(FullscreenRule.fillsMonitor(rect(0, 0, 3440, 1440), MONITOR, WS_MAXIMIZE));
    }

    @Test
    void screenSizedWindowWithAFrameFillsTheMonitor() {
        assertTrue(FullscreenRule.fillsMonitor(rect(0, 0, 3456, 1479), MONITOR, WS_CAPTION));
    }

    @Test
    void maximizedWindowAboveTheTaskbarIsOrdinary() {
        assertFalse(FullscreenRule.fillsMonitor(rect(-8, -8, 3448, 1396), MONITOR, WS_MAXIMIZE | WS_CAPTION));
    }

    @Test
    void maximizedWindowWithAnAutoHiddenTaskbarIsOrdinary() {
        assertFalse(FullscreenRule.fillsMonitor(rect(-8, -8, 3448, 1448), MONITOR, WS_MAXIMIZE | WS_CAPTION));
    }

    @Test
    void smallerWindowIsOrdinary() {
        assertFalse(FullscreenRule.fillsMonitor(rect(1000, 244, 2440, 1144), MONITOR, 0));
    }

    @Test
    void windowOnAnotherMonitorDoesNotCoverThisOne() {
        assertFalse(FullscreenRule.fillsMonitor(rect(3440, 0, 5360, 1080), MONITOR, 0));
    }

    @Test
    void desktopTaskbarAndShellOverlaysAreNeverFullscreenApps() {
        assertTrue(FullscreenRule.isShellWindow("Progman", "explorer.exe"));
        assertTrue(FullscreenRule.isShellWindow("WorkerW", "explorer.exe"));
        assertTrue(FullscreenRule.isShellWindow("Windows.UI.Core.CoreWindow", "SearchHost.exe"));
        assertTrue(FullscreenRule.isShellWindow("Windows.UI.Core.CoreWindow", "StartMenuExperienceHost.exe"));
    }

    @Test
    void appsAreNotShellWindows() {
        assertFalse(FullscreenRule.isShellWindow("MozillaWindowClass", "firefox.exe"));
        assertFalse(FullscreenRule.isShellWindow("UnityWndClass", "Big Ambitions.exe"));
        assertFalse(FullscreenRule.isShellWindow("Chrome_WidgetWin_1", ""));
    }

    private static RECT rect(int left, int top, int right, int bottom) {
        var rect = new RECT();
        rect.left = left;
        rect.top = top;
        rect.right = right;
        rect.bottom = bottom;

        return rect;
    }
}
