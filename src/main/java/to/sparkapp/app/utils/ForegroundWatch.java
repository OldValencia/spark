package to.sparkapp.app.utils;

import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.DWORD;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.LONG;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.platform.win32.WinUser.WinEventProc;
import com.sun.jna.ptr.IntByReference;
import lombok.extern.slf4j.Slf4j;

/**
 * Reports when another app comes to the foreground or the foreground window changes its size, for example when
 * a browser enters fullscreen. Windows delivers the events to the thread that started the watch, so start and
 * stop it on the JavaFX thread; nothing runs while no window changes.
 */
@Slf4j
public final class ForegroundWatch {
    private static final int EVENT_SYSTEM_FOREGROUND = 0x0003;
    private static final int EVENT_OBJECT_LOCATIONCHANGE = 0x800B;
    private static final int WINEVENT_SKIPOWNPROCESS = 0x0002;
    private static final int OBJID_WINDOW = 0;
    private static final int CHILDID_SELF = 0;

    private final Runnable changed;
    // Held in fields: JNA callbacks must stay reachable while Windows can call them.
    private final WinEventProc foregroundEvents = this::foregroundEvent;
    private final WinEventProc locationEvents = this::locationEvent;
    private HANDLE foregroundHook;
    private HANDLE locationHook;
    private HWND foreground;

    public ForegroundWatch(Runnable changed) {
        this.changed = changed;
    }

    public void start() {
        if (foregroundHook != null || !SystemUtils.isWindows()) {
            return;
        }

        foregroundHook = User32.INSTANCE.SetWinEventHook(EVENT_SYSTEM_FOREGROUND, EVENT_SYSTEM_FOREGROUND, null,
                foregroundEvents, 0, 0, WINEVENT_SKIPOWNPROCESS);

        if (foregroundHook == null) {
            log.warn("Could not watch the foreground window");

            return;
        }

        watchLocation(User32.INSTANCE.GetForegroundWindow());
    }

    public void stop() {
        unhook(locationHook);
        unhook(foregroundHook);
        locationHook = null;
        foregroundHook = null;
        foreground = null;
    }

    @SuppressWarnings("unused")
    private void foregroundEvent(HANDLE hook, DWORD event, HWND window, LONG object, LONG child, DWORD thread,
                                 DWORD time) {
        if (foregroundHook == null) {
            return;
        }

        watchLocation(window);
        changed.run();
    }

    @SuppressWarnings("unused")
    private void locationEvent(HANDLE hook, DWORD event, HWND window, LONG object, LONG child, DWORD thread,
                               DWORD time) {
        if (locationHook != null && object.intValue() == OBJID_WINDOW && child.intValue() == CHILDID_SELF &&
                window != null && window.equals(foreground)) {
            changed.run();
        }
    }

    /** Listens only to the foreground app's own windows, so other apps' activity never reaches Spark. */
    private void watchLocation(HWND window) {
        unhook(locationHook);
        locationHook = null;
        foreground = window;

        if (window == null) {
            return;
        }

        var processId = new IntByReference();
        User32.INSTANCE.GetWindowThreadProcessId(window, processId);

        if (processId.getValue() != 0) {
            locationHook = User32.INSTANCE.SetWinEventHook(EVENT_OBJECT_LOCATIONCHANGE, EVENT_OBJECT_LOCATIONCHANGE,
                    null, locationEvents, processId.getValue(), 0, WINEVENT_SKIPOWNPROCESS);
        }
    }

    private static void unhook(HANDLE hook) {
        if (hook != null) {
            User32.INSTANCE.UnhookWinEvent(hook);
        }
    }
}
