package to.sparkapp.app.windows;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.support.JavaFxTestRuntime;

@Tag("native")
@Timeout(20)
class WindowMotionTest {
    @Test
    void reversalCancelsThePendingHideAndPersistsRestingGeometry() throws Exception {
        var preferences = JavaFxTestRuntime.call(AppPreferences::new);
        boolean previousMotion = preferences.isReducedMotion();
        var completed = new AtomicInteger();
        var stage = JavaFxTestRuntime.call(() -> {
            preferences.setReducedMotion(false);
            var window = new Stage();
            window.setScene(new Scene(new StackPane(), 400, 300));
            window.setX(-20000);
            window.setY(-20000);
            window.show();

            return window;
        });
        var motion = JavaFxTestRuntime.call(() -> {
            var animation = new WindowMotion(stage, preferences, () -> true, () -> -20000, state -> {
            });
            animation.open();
            assertEquals(-20000, animation.restingY());
            animation.hide(completed::incrementAndGet);
            animation.open();

            return animation;
        });

        try {
            awaitStopped(motion);
            JavaFxTestRuntime.call(() -> {
                assertTrue(stage.isShowing());
                assertEquals(-20000, stage.getY(), 0.1);
                assertEquals(0, completed.get(), "Reopening must cancel the queued hide");
                preferences.setReducedMotion(true);
                motion.hide(() -> {
                    completed.incrementAndGet();
                    stage.hide();
                });
                assertFalse(stage.isShowing());
                assertFalse(motion.running());
                assertEquals(-20000, stage.getY(), 0.1);
                assertEquals(1, completed.get());

                return null;
            });
        } finally {
            JavaFxTestRuntime.call(() -> {
                motion.settle();
                stage.close();
                preferences.setReducedMotion(previousMotion);
                preferences.flush();

                return null;
            });
        }
    }

    private void awaitStopped(WindowMotion motion) throws Exception {
        var deadline = System.nanoTime() + 3_000_000_000L;

        while (System.nanoTime() < deadline) {
            if (JavaFxTestRuntime.call(() -> !motion.running())) {
                return;
            }

            Thread.sleep(20);
        }

        throw new AssertionError("The window animation did not settle");
    }
}
