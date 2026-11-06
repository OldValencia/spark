package to.sparkapp.app.windows;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PointerMotionTest {
    private static final long START = 1_000_000_000L;
    private final PointerMotion motion = new PointerMotion();

    @Test
    void detectsADeliberateFastUpwardApproach() {
        motion.observe(500, 220, START);
        motion.observe(505, 140, START + 40_000_000);
        var approach = motion.observe(510, 0, START + 100_000_000);

        assertTrue(approach.isFastUpward(1, 1, START + 110_000_000));
    }

    @Test
    void slowMovementAndSmallEdgeJitterDoNotOpenTheWindow() {
        motion.observe(500, 100, START);
        var slow = motion.observe(500, 0, START + 150_000_000);
        assertFalse(slow.isFastUpward(1, 1, START + 150_000_000));

        motion.reset();
        motion.observe(500, 15, START);
        var jitter = motion.observe(500, 0, START + 8_000_000);
        assertFalse(jitter.isFastUpward(1, 1, START + 8_000_000));
    }

    @Test
    void horizontalAndDiagonalPassesDoNotCountAsUpwardGestures() {
        motion.observe(100, 180, START);
        var diagonal = motion.observe(400, 0, START + 80_000_000);
        assertFalse(diagonal.isFastUpward(1, 1, START + 80_000_000));

        motion.reset();
        motion.observe(100, 0, START);
        var horizontal = motion.observe(400, 0, START + 80_000_000);
        assertFalse(horizontal.isFastUpward(1, 1, START + 80_000_000));
    }

    @Test
    void downwardMotionAndPausesDiscardThePreviousApproach() {
        motion.observe(500, 200, START);
        motion.observe(500, 50, START + 40_000_000);
        var reverse = motion.observe(500, 70, START + 50_000_000);
        assertFalse(reverse.isFastUpward(1, 1, START + 50_000_000));

        var afterPause = motion.observe(500, 0, START + 400_000_000);
        assertFalse(afterPause.isFastUpward(1, 1, START + 400_000_000));
    }

    @Test
    void delayedUiUpdatesCannotReplayAnOldGesture() {
        motion.observe(500, 200, START);
        var approach = motion.observe(500, 0, START + 80_000_000);

        assertFalse(approach.isFastUpward(1, 1, START + 300_000_000));
    }

    @Test
    void thresholdsUseLogicalPixelsOnScaledMonitors() {
        motion.observe(-500, -80, START);
        var fast = motion.observe(-500, -400, START + 100_000_000);
        assertTrue(fast.isFastUpward(2, 2, START + 100_000_000));

        motion.reset();
        motion.observe(-500, -280, START);
        var shortApproach = motion.observe(-500, -400, START + 40_000_000);
        assertFalse(shortApproach.isFastUpward(2, 2, START + 40_000_000));
    }

    @Test
    void highFrequencyEventsRetainEnoughHistoryForACompleteGesture() {
        for (var step = 0; step < 100; step++) {
            motion.observe(500, 200 - step * 2, START + step * 1_000_000L);
        }
        var approach = motion.observe(500, 0, START + 100_000_000);

        assertTrue(approach.isFastUpward(1, 1, START + 100_000_000));
    }
}
