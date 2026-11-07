package to.sparkapp.app.utils;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import com.sun.jna.platform.win32.User32;
import com.sun.jna.ptr.IntByReference;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Recognizes owned dialogs and WebView2 popup windows without inspecting their content.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class WindowsInteraction {
    static boolean ownsForeground() {
        var foreground = User32.INSTANCE.GetForegroundWindow();

        if (foreground == null) {
            return false;
        }

        var owner = User32.INSTANCE.GetAncestor(foreground, 3);
        var process = new IntByReference();
        User32.INSTANCE.GetWindowThreadProcessId(owner != null ? owner : foreground, process);
        var processId = Integer.toUnsignedLong(process.getValue());

        return processId == ProcessHandle.current().pid() || isBrowserDescendant(processId);
    }

    static boolean hasModalWindow() {
        var modal = new AtomicBoolean();
        User32.INSTANCE.EnumWindows((window, data) -> {
            if (User32.INSTANCE.IsWindowVisible(window) && !User32.INSTANCE.IsWindowEnabled(window)) {
                var process = new IntByReference();
                User32.INSTANCE.GetWindowThreadProcessId(window, process);
                var processId = Integer.toUnsignedLong(process.getValue());

                if (processId == ProcessHandle.current().pid() || isBrowserDescendant(processId)) {
                    modal.set(true);

                    return false;
                }
            }

            return true;
        }, null);

        return modal.get();
    }

    private static boolean isBrowserDescendant(long processId) {
        var process = ProcessHandle.of(processId).orElse(null);

        if (process == null) {
            return false;
        }

        var command = process.info().command().orElse("");

        if (command.isEmpty() || !Path.of(command).getFileName().toString().equalsIgnoreCase("msedgewebview2.exe")) {
            return false;
        }

        while (process != null) {
            if (process.pid() == ProcessHandle.current().pid()) {
                return true;
            }

            process = process.parent().orElse(null);
        }

        return false;
    }
}
