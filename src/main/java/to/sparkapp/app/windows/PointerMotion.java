package to.sparkapp.app.windows;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Retains a short native-event history before JavaFX coalesces pointer updates.
 */
final class PointerMotion {
    private static final long SAMPLE_INTERVAL_NANOS = 8_000_000L;
    private final Deque<Sample> samples = new ArrayDeque<>();
    private Sample previous;

    synchronized PointerApproach observe(double x, double y, long now) {
        if (previous != null && (now <= previous.time() || y > previous.y() + 3)) {
            samples.clear();
        }

        while (!samples.isEmpty() && now - samples.getFirst().time() > PointerApproach.MAX_AGE_NANOS) {
            samples.removeFirst();
        }

        var current = new Sample(x, y, now);
        var origin = samples.isEmpty() ? current : samples.getFirst();
        if (samples.isEmpty() || now - samples.getLast().time() >= SAMPLE_INTERVAL_NANOS) {
            samples.addLast(current);
        }
        previous = current;

        return new PointerApproach(x - origin.x(), origin.y() - y, now - origin.time(), now);
    }

    synchronized void reset() {
        samples.clear();
        previous = null;
    }

    private record Sample(double x, double y, long time) {
    }
}
