package to.sparkapp.app.windows;

import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javafx.animation.PauseTransition;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import to.sparkapp.app.support.JavaFxTestRuntime;
import to.sparkapp.app.utils.NativeWindowUtils;

/**
 * Prints what the pill would do as real apps come and go or enter fullscreen, and how many window events reach
 * Spark. A manual check: switch apps while it runs. Run with SPARK_FULLSCREEN_WATCH=1;
 * SPARK_FULLSCREEN_WATCH_SECONDS sets how long (default 60).
 */
@Tag("native")
@EnabledIfEnvironmentVariable(named = "SPARK_FULLSCREEN_WATCH", matches = "1")
class FullscreenWatchProbe {
    @Test
    @Timeout(value = 30, unit = TimeUnit.MINUTES)
    void watch() throws Exception {
        var seconds = Long.parseLong(System.getenv().getOrDefault("SPARK_FULLSCREEN_WATCH_SECONDS", "60"));
        var checks = new AtomicInteger();
        var holder = new Object[2];

        JavaFxTestRuntime.call(() -> {
            var marker = new Stage();
            marker.setWidth(1);
            marker.setHeight(1);
            marker.setX(Screen.getPrimary().getVisualBounds().getMinX());
            marker.setY(Screen.getPrimary().getVisualBounds().getMinY());
            marker.show();

            var sensor = new NativeFullscreenSensor(() -> Screen.getPrimary().getVisualBounds());
            sensor.pillShown(NativeWindowUtils.windowHandle(marker));
            marker.hide();
            sensor.watch(() -> report(sensor, checks.incrementAndGet()));
            report(sensor, 0);
            holder[0] = sensor;
            holder[1] = marker;

            return null;
        });

        Thread.sleep(TimeUnit.SECONDS.toMillis(seconds));

        JavaFxTestRuntime.call(() -> {
            ((NativeFullscreenSensor) holder[0]).stopWatching();
            ((Stage) holder[1]).close();

            return null;
        });
        System.out.printf("%d settled checks in %d s%n", checks.get(), seconds);
    }

    private static void report(NativeFullscreenSensor sensor, int check) {
        print(sensor, "check " + check);
        var later = new PauseTransition(Duration.seconds(1));
        later.setOnFinished(event -> print(sensor, "  1 s after check " + check));
        later.play();
    }

    private static void print(NativeFullscreenSensor sensor, String label) {
        System.out.printf("%s %s: pill %s, under pointer %s%n", LocalTime.now().truncatedTo(ChronoUnit.MILLIS), label,
                sensor.isOnPillMonitor() ? "HIDDEN (fullscreen)" : "shown", sensor.isUnderPointer());
    }
}
