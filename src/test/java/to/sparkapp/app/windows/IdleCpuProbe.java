package to.sparkapp.app.windows;

import java.awt.MouseInfo;
import java.awt.Robot;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.sun.management.OperatingSystemMXBean;
import com.sun.net.httpserver.HttpServer;
import javafx.application.Platform;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import to.sparkapp.app.config.AiConfiguration;
import to.sparkapp.app.config.AiConfiguration.AiConfig;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.support.JavaFxTestRuntime;

/**
 * Measures the Spark process's CPU while the window is hidden or shown, with the mouse still or
 * moving. A manual measurement: it moves the real mouse. Run with SPARK_CPU_PROBE=1.
 */
@Tag("native")
@EnabledIfEnvironmentVariable(named = "SPARK_CPU_PROBE", matches = "1")
class IdleCpuProbe {
    private static final long PHASE_MS = 10_000;

    @Test
    @Timeout(180)
    void measure() throws Exception {
        JavaFxTestRuntime.start();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            var bytes = "<!doctype html><title>Idle</title><p>Idle page</p>".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        var preferences = fx(AppPreferences::new);
        var window = fx(() -> {
            preferences.setCheckUpdatesOnStartup(false);
            preferences.setTutorialDone(true);
            // SPARK_PROBE_EDGE=0 separates the native hook's own cost from Spark's pointer handling.
            preferences.setTopEdgeReveal(!"0".equals(System.getenv("SPARK_PROBE_EDGE")));
            var sites = new AiConfiguration(preferences);
            sites.getCustomProvidersManager().saveProviders(List.of(new AiConfig("cpu-probe", "Probe",
                    "http://127.0.0.1:" + server.getAddress().getPort(), "#777777", "chatgpt.svg")));
            sites.reload();
            var main = new MainWindow(sites, preferences);
            main.showWindow();

            return main;
        });

        try {
            Thread.sleep(5000);
            System.out.println("RESULT pill=" + preferences.isTopPill() + " edgeReveal=" + preferences.isTopEdgeReveal() +
                    " autoHide=" + preferences.isAutoHide() + " mascot=" + preferences.isMascotEnabled());
            fx(() -> {
                window.hideMainWindow();

                return null;
            });
            Thread.sleep(2000);
            phase("hidden, mouse still", false);
            phase("hidden, mouse moving", true);
            fx(() -> {
                window.showMainWindow();

                return null;
            });
            Thread.sleep(2000);
            phase("shown, mouse still", false);
            phase("shown, mouse moving", true);
        } finally {
            fx(() -> {
                window.hideMainWindow();

                return null;
            });
            server.stop(0);
        }
    }

    private static void phase(String name, boolean moveMouse) throws Exception {
        var os = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
        var gcCount = gcCount();
        var gcTime = gcTime();
        var cpu = os.getProcessCpuTime();
        var started = System.nanoTime();
        var moves = moveMouse ? moveMouse(PHASE_MS) : sleep(PHASE_MS);
        var wall = System.nanoTime() - started;
        var used = os.getProcessCpuTime() - cpu;

        System.out.printf("RESULT %-22s cpu=%5.2f%% of one core  gc=%d (%d ms)  moves=%d%n", name,
                100.0 * used / wall, gcCount() - gcCount, gcTime() - gcTime, moves);
    }

    private static long sleep(long millis) throws InterruptedException {
        Thread.sleep(millis);

        return 0;
    }

    /** Moves the real mouse in small circles, about 120 times a second, then puts it back. */
    private static long moveMouse(long millis) throws Exception {
        var robot = new Robot();
        var previous = MouseInfo.getPointerInfo().getLocation();
        // Always the same spot away from the Spark window, so runs compare.
        var screen = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        var origin = new java.awt.Point(screen.x + 150, screen.y + screen.height - 150);
        var deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
        var moves = 0L;
        var stopped = new AtomicBoolean();

        try {
            while (System.nanoTime() < deadline && !stopped.get()) {
                var angle = moves * 0.15;
                robot.mouseMove(origin.x + (int) (Math.cos(angle) * 60), origin.y + (int) (Math.sin(angle) * 60));
                moves++;
                Thread.sleep(8);
            }
        } finally {
            robot.mouseMove(previous.x, previous.y);
        }

        return moves;
    }

    private static long gcCount() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(GarbageCollectorMXBean::getCollectionCount).sum();
    }

    private static long gcTime() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(GarbageCollectorMXBean::getCollectionTime).sum();
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        var task = new FutureTask<>(action);
        Platform.runLater(task);

        return task.get(15, TimeUnit.SECONDS);
    }
}
