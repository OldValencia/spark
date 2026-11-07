package to.sparkapp.app.windows;

import java.util.function.DoubleConsumer;

import javafx.animation.AnimationTimer;
import javafx.animation.Interpolator;

/** Starts elapsed time on the first pulse, after synchronous window restoration. */
final class WindowPositionAnimation extends AnimationTimer {
    private final DoubleConsumer position;
    private double origin;
    private double target;
    private double durationNanos;
    private long startedAt;
    private Interpolator easing;
    private Runnable completion;
    private boolean running;

    WindowPositionAnimation(DoubleConsumer position) {
        this.position = position;
    }

    boolean running() {
        return running;
    }

    void play(double origin, double target, double milliseconds, Interpolator easing, Runnable completion) {
        stop();
        this.origin = origin;
        this.target = target;
        this.durationNanos = milliseconds * 1_000_000;
        this.easing = easing;
        this.completion = completion;
        startedAt = 0;
        running = true;
        start();
    }

    @Override
    public void handle(long now) {
        if (!running) {
            return;
        }

        if (startedAt == 0) {
            startedAt = now;
        }

        var progress = Math.clamp((now - startedAt) / durationNanos, 0, 1);
        position.accept(easing.interpolate(origin, target, progress));

        if (progress == 1) {
            var completed = completion;
            stop();
            completed.run();
        }
    }

    @Override
    public void stop() {
        super.stop();
        running = false;
        completion = null;
    }
}
