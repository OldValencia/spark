package to.sparkapp.app.windows;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import javafx.animation.Interpolator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import to.sparkapp.app.support.JavaFxTestRuntime;

@Tag("native")
@Timeout(20)
class WindowPositionAnimationTest {
    @Test
    void delayedFirstPulsePreservesTheFullAnimationAndReversalCancelsCompletion() throws Exception {
        JavaFxTestRuntime.call(() -> {
            var positions = new ArrayList<Double>();
            var opened = new AtomicInteger();
            var hidden = new AtomicInteger();
            var animation = new WindowPositionAnimation(positions::add);

            try {
                animation.play(-700, 0, 340, Interpolator.EASE_BOTH, opened::incrementAndGet);
                animation.handle(5_000_000_000L);
                assertEquals(-700, positions.getLast());
                assertTrue(animation.running());
                assertEquals(0, opened.get());

                animation.handle(5_170_000_000L);
                var halfway = positions.getLast();
                assertTrue(halfway > -700 && halfway < 0);

                animation.play(halfway, -700, 200, Interpolator.EASE_BOTH, hidden::incrementAndGet);
                animation.handle(6_000_000_000L);
                assertEquals(halfway, positions.getLast());
                animation.handle(6_200_000_000L);
                assertEquals(-700, positions.getLast());
                assertFalse(animation.running());
                assertEquals(0, opened.get());
                assertEquals(1, hidden.get());

                animation.handle(7_000_000_000L);
                assertEquals(1, hidden.get());
            } finally {
                animation.stop();
            }

            return null;
        });
    }
}
