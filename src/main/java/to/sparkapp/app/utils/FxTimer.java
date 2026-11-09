package to.sparkapp.app.utils;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;
import javafx.util.Duration;

/**
 * Runs an action on the JavaFX thread after a delay, or at a fixed period.
 *
 * <p>A JavaFX animation used as a timer, such as {@code PauseTransition}, keeps JavaFX drawing 60
 * frames a second while it runs, even when nothing changes. This timer waits on a background thread
 * instead, so an idle Spark does not wake the CPU. Start and stop it on the JavaFX thread.
 */
public final class FxTimer {
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(task -> {
        var thread = new Thread(task, "spark-fx-timer");
        thread.setDaemon(true);

        return thread;
    });

    private final Runnable action;
    private final boolean repeating;
    private Duration delay;
    private ScheduledFuture<?> scheduled;
    /** Drops a run that was already queued when the timer was stopped or restarted. */
    private long generation;

    private FxTimer(Duration delay, Runnable action, boolean repeating) {
        this.delay = delay;
        this.action = action;
        this.repeating = repeating;
    }

    public static FxTimer once(Duration delay, Runnable action) {
        return new FxTimer(delay, action, false);
    }

    public static FxTimer repeating(Duration period, Runnable action) {
        return new FxTimer(period, action, true);
    }

    public void setDelay(Duration delay) {
        this.delay = delay;
    }

    /** Starts the wait again from now, dropping a pending run. */
    public void restart() {
        stop();
        var run = generation;
        var millis = Math.max(0, Math.round(delay.toMillis()));
        Runnable fire = () -> Platform.runLater(() -> fire(run));

        if (repeating) {
            scheduled = SCHEDULER.scheduleAtFixedRate(fire, millis, Math.max(1, millis), TimeUnit.MILLISECONDS);
        } else {
            scheduled = SCHEDULER.schedule(fire, millis, TimeUnit.MILLISECONDS);
        }
    }

    /** Starts the timer unless it is already running. */
    public void start() {
        if (!isRunning()) {
            restart();
        }
    }

    public void stop() {
        generation++;

        if (scheduled != null) {
            scheduled.cancel(false);
            scheduled = null;
        }
    }

    public boolean isRunning() {
        return scheduled != null;
    }

    private void fire(long run) {
        if (run != generation) {
            return;
        }

        if (!repeating) {
            scheduled = null;
        }

        action.run();
    }
}
