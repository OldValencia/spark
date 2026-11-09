package to.sparkapp.app.windows;

import java.time.Duration;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.win32.StdCallLibrary;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import to.sparkapp.app.utils.FullscreenDetector;

/**
 * Prints both fullscreen signals whenever they change, so they can be compared against real games,
 * videos, presentations and maximized windows. A manual measurement: switch between apps while it
 * runs. Run with SPARK_FULLSCREEN_PROBE=1; SPARK_FULLSCREEN_PROBE_MINUTES sets how long (default 5).
 */
@Tag("native")
@EnabledIfEnvironmentVariable(named = "SPARK_FULLSCREEN_PROBE", matches = "1")
class FullscreenProbe {
    private static final Pointer PER_MONITOR_AWARE_V2 = new Pointer(-4);

    private interface Dpi extends StdCallLibrary {
        Dpi INSTANCE = Native.load("user32", Dpi.class);

        boolean SetProcessDpiAwarenessContext(Pointer context);
    }

    @Test
    @Timeout(value = 65, unit = TimeUnit.MINUTES)
    void watch() throws Exception {
        // Spark is per-monitor aware; without this, scaled monitors report virtualized rectangles.
        System.out.println("Per-monitor DPI awareness set: " + Dpi.INSTANCE.SetProcessDpiAwarenessContext(
                PER_MONITOR_AWARE_V2));

        var minutes = Long.parseLong(System.getenv().getOrDefault("SPARK_FULLSCREEN_PROBE_MINUTES", "5"));
        var deadline = System.nanoTime() + Duration.ofMinutes(minutes).toNanos();
        FullscreenSignals previous = null;

        while (System.nanoTime() < deadline) {
            var current = FullscreenSignals.read();

            if (!current.equals(previous)) {
                print(current);
                previous = current;
            }

            Thread.sleep(300);
        }
    }

    private static void print(FullscreenSignals signals) {
        System.out.printf("%s spark=%-5s shell=%-5s geometry=%-5s | %s | %s %s | window %s monitor %s | maximized=%s "
                        + "caption=%s covers=%s cursorMonitor=%s ignored=%s%n",
                LocalTime.now().truncatedTo(ChronoUnit.SECONDS), FullscreenDetector.isFullscreenUnderPointer(),
                signals.shellFullscreen(),
                signals.geometryFullscreen(), signals.notificationState(), signals.process(),
                signals.windowClass(), signals.windowRect(), signals.monitorRect(), signals.maximized(),
                signals.caption(), signals.coversMonitor(), signals.cursorMonitor(), signals.ignoredWindow());
    }
}
