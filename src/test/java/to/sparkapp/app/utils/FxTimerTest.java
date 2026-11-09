package to.sparkapp.app.utils;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicInteger;

import javafx.application.Platform;
import javafx.util.Duration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import to.sparkapp.app.support.JavaFxTestRuntime;

@Tag("native")
@Timeout(20)
class FxTimerTest {
    @Test
    void delayRunsOnceOnTheJavaFxThread() throws Exception {
        var runs = new AtomicInteger();
        var onFxThread = new AtomicInteger();
        var timer = JavaFxTestRuntime.call(() -> FxTimer.once(Duration.millis(100), () -> {
            runs.incrementAndGet();

            if (Platform.isFxApplicationThread()) {
                onFxThread.incrementAndGet();
            }
        }));

        call(timer::restart);
        Thread.sleep(400);

        assertEquals(1, runs.get());
        assertEquals(1, onFxThread.get());
        assertFalse(JavaFxTestRuntime.call(timer::isRunning));
    }

    @Test
    void restartingWaitsAgainAndStoppingCancels() throws Exception {
        var runs = new AtomicInteger();
        var timer = JavaFxTestRuntime.call(() -> FxTimer.once(Duration.millis(200), runs::incrementAndGet));

        call(timer::restart);
        Thread.sleep(120);
        call(timer::restart);
        Thread.sleep(120);
        assertEquals(0, runs.get(), "A restart pushes the run back");

        Thread.sleep(200);
        assertEquals(1, runs.get());

        call(timer::restart);
        call(timer::stop);
        Thread.sleep(300);
        assertEquals(1, runs.get(), "A stopped timer does not run");
    }

    @Test
    void repeatingTimerRunsUntilStopped() throws Exception {
        var runs = new AtomicInteger();
        var timer = JavaFxTestRuntime.call(() -> FxTimer.repeating(Duration.millis(50), runs::incrementAndGet));

        call(timer::start);
        call(timer::start);
        Thread.sleep(330);
        call(timer::stop);
        var counted = runs.get();
        Thread.sleep(200);

        assertTrue(counted >= 4 && counted <= 8, "About one run per period, not doubled: " + counted);
        assertEquals(counted, runs.get());
    }

    private static void call(Runnable action) throws Exception {
        JavaFxTestRuntime.call(() -> {
            action.run();

            return null;
        });
    }
}
