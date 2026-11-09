package to.sparkapp.app.ui.topbar.components;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import to.sparkapp.app.support.JavaFxTestRuntime;

@Tag("native")
@Timeout(20)
class PageLoadingBarTest {
    private final AtomicBoolean reducedMotion = new AtomicBoolean();

    @Test
    void quickLoadsLeaveNoTrace() throws Exception {
        var bar = JavaFxTestRuntime.call(() -> new PageLoadingBar(reducedMotion::get));

        setLoading(bar, true);
        Thread.sleep(100);
        setLoading(bar, false);
        Thread.sleep(400);

        assertFalse(JavaFxTestRuntime.call(bar::isShowing));
    }

    @Test
    void slowLoadCreepsAndThenCompletes() throws Exception {
        var bar = JavaFxTestRuntime.call(() -> new PageLoadingBar(reducedMotion::get));

        setLoading(bar, true);
        Thread.sleep(800);

        assertTrue(JavaFxTestRuntime.call(bar::isShowing));
        var creeping = JavaFxTestRuntime.call(bar::getProgress);
        assertTrue(creeping > 0 && creeping < 0.9, "The line creeps but never pretends to be done");

        setLoading(bar, false);
        Thread.sleep(120);
        assertTrue(JavaFxTestRuntime.call(bar::getProgress) > creeping, "Finishing fills the line");

        Thread.sleep(600);
        assertFalse(JavaFxTestRuntime.call(bar::isShowing));
        assertEquals(0, JavaFxTestRuntime.call(bar::getProgress));
    }

    @Test
    void reducedMotionShowsAStillLine() throws Exception {
        reducedMotion.set(true);
        var bar = JavaFxTestRuntime.call(() -> new PageLoadingBar(reducedMotion::get));

        setLoading(bar, true);
        Thread.sleep(500);

        assertTrue(JavaFxTestRuntime.call(bar::isShowing));
        assertEquals(1, JavaFxTestRuntime.call(bar::getProgress));

        setLoading(bar, false);
        assertFalse(JavaFxTestRuntime.call(bar::isShowing));
    }

    private static void setLoading(PageLoadingBar bar, boolean loading) throws Exception {
        JavaFxTestRuntime.call(() -> {
            bar.setLoading(loading);

            return null;
        });
    }
}
