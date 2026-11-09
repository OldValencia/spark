package to.sparkapp.app.windows;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpServer;
import javafx.application.Platform;
import javafx.scene.robot.Robot;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import to.sparkapp.app.config.AiConfiguration;
import to.sparkapp.app.config.AiConfiguration.AiConfig;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.support.JavaFxTestRuntime;

/**
 * Measures how long the real Spark window takes to show its page again after being hidden. A manual
 * measurement, not a check: it moves a window on screen and takes minutes.
 *
 * <p>Run with SPARK_SHOW_PROBE=1 and optionally SPARK_PROBE_URL (a light page), SPARK_PROBE_HIDDEN
 * (milliseconds, comma-separated) and SPARK_PROBE_RUNS. Results are printed as RESULT lines.
 */
@Tag("native")
@EnabledIfEnvironmentVariable(named = "SPARK_SHOW_PROBE", matches = "1")
class MainWindowShowProbe {
    private static final String URL = System.getenv("SPARK_PROBE_URL");
    private static final long[] HIDDEN = Arrays.stream(
            setting("SPARK_PROBE_HIDDEN", "1000,8000,30000").split(",")).mapToLong(Long::parseLong).toArray();
    private static final int RUNS = Integer.parseInt(setting("SPARK_PROBE_RUNS", "3"));
    /** Light pages count as painted; the page color is checked when testing the local fixture. */
    private static final boolean LIGHT_PAGE = URL != null;

    private static String setting(String name, String fallback) {
        var value = System.getenv(name);

        return value == null ? fallback : value;
    }
    @Test
    @Timeout(1800)
    void measure() throws Exception {
        JavaFxTestRuntime.start();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            var bytes = "<!doctype html><body style='margin:0;background:#ff00ff;height:100vh'></body>"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        var preferences = fx(AppPreferences::new);
        var window = fx(() -> {
            preferences.setCheckUpdatesOnStartup(false);
            preferences.setTutorialDone(true);
            preferences.setAlwaysOnTop(true);
            var sites = new AiConfiguration(preferences);
            preferences.setDarkModeEnabled(false);
            var url = URL != null ? URL : "http://127.0.0.1:" + server.getAddress().getPort();
            sites.getCustomProvidersManager().saveProviders(List.of(new AiConfig("show-probe", "Probe", url,
                    "#777777", "chatgpt.svg")));
            sites.reload();
            var main = new MainWindow(sites, preferences);
            main.showWindow();

            return main;
        });

        try {
            System.out.println("RESULT first=" + untilPainted(window, 20_000) + "ms");
            Thread.sleep(1500);

            for (var reduce : new boolean[]{false, true}) {
                fx(() -> {
                    preferences.setBackgroundMemoryReductionEnabled(reduce);

                    return null;
                });

                for (var hiddenMs : HIDDEN) {
                    var runs = new StringBuilder();

                    for (var run = 0; run < RUNS; run++) {
                        fx(() -> {
                            window.hideMainWindow();

                            return null;
                        });
                        Thread.sleep(hiddenMs);
                        fx(() -> {
                            window.showMainWindow();

                            return null;
                        });
                        runs.append(" show").append(run + 1).append('=').append(untilPainted(window, 15_000))
                                .append("ms");
                        Thread.sleep(800);
                    }

                    System.out.println("RESULT reduce=" + reduce + " hidden=" + hiddenMs + "ms ->" + runs);
                }
            }
        } finally {
            server.stop(0);
        }
    }

    private static long untilPainted(MainWindow window, long timeoutMs) throws Exception {
        var started = System.nanoTime();
        var deadline = started + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        var robot = fx(Robot::new);

        while (System.nanoTime() < deadline) {
            var color = fx(() -> robot.getPixelColor(window.getX() + window.getWidth() / 2,
                    window.getY() + window.getHeight() / 2));

            var painted = LIGHT_PAGE ? color.getBrightness() > 0.85 :
                    color.getRed() > 0.9 && color.getGreen() < 0.1 && color.getBlue() > 0.9;

            if (window.isShowing() && painted) {
                return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            }

            Thread.sleep(5);
        }

        return -1;
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        var task = new FutureTask<>(action);
        Platform.runLater(task);

        return task.get(15, TimeUnit.SECONDS);
    }
}
