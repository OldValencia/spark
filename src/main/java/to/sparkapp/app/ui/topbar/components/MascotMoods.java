package to.sparkapp.app.ui.topbar.components;

import java.util.function.Consumer;

import javafx.animation.PauseTransition;
import javafx.util.Duration;
import lombok.Getter;

/**
 * Decides the mascot's mood: thinking while a page takes a moment to load, sad when it fails, and
 * asleep after a long time without activity in Spark.
 */
final class MascotMoods {
    static final Duration THINKING_DELAY = Duration.millis(300);
    static final Duration SLEEP_AFTER = Duration.minutes(5);

    private final Consumer<MascotMood> changed;
    private final PauseTransition thinkingDelay = new PauseTransition(THINKING_DELAY);
    private final PauseTransition sleepTimer;
    private boolean active;
    private boolean loading;
    private boolean failed;
    private boolean thinking;
    @Getter
    private boolean asleep;
    @Getter
    private MascotMood mood = MascotMood.CALM;

    MascotMoods(Consumer<MascotMood> changed) {
        this(changed, SLEEP_AFTER);
    }

    MascotMoods(Consumer<MascotMood> changed, Duration sleepAfter) {
        this.changed = changed;
        sleepTimer = new PauseTransition(sleepAfter);
        thinkingDelay.setOnFinished(event -> {
            thinking = loading;
            update();
        });
        sleepTimer.setOnFinished(event -> {
            asleep = true;
            update();
        });
    }

    /** Moods show only while the mascot is visible and allowed to move. */
    void setActive(boolean active) {
        this.active = active;

        if (active) {
            sleepTimer.playFromStart();

            if (loading) {
                thinkingDelay.playFromStart();
            }
        } else {
            thinkingDelay.stop();
            sleepTimer.stop();
            thinking = false;
        }

        update();
    }

    void setLoading(boolean loading) {
        this.loading = loading;
        thinkingDelay.stop();
        thinking = false;

        if (loading) {
            wake();

            if (active) {
                thinkingDelay.playFromStart();
            }
        }

        update();
    }

    void setFailed(boolean failed) {
        this.failed = failed;
        update();
    }

    /**
     * Records activity in Spark.
     *
     * @return whether the mascot was asleep
     */
    boolean wake() {
        var wasAsleep = asleep;
        asleep = false;

        if (active) {
            sleepTimer.playFromStart();
        }

        update();

        return wasAsleep;
    }

    void dispose() {
        setActive(false);
    }

    private void update() {
        var next = resolve();

        if (next != mood) {
            mood = next;
            changed.accept(next);
        }
    }

    private MascotMood resolve() {
        if (!active) {
            return MascotMood.CALM;
        }

        if (failed) {
            return MascotMood.SAD;
        }

        if (thinking) {
            return MascotMood.THINKING;
        }

        return asleep ? MascotMood.ASLEEP : MascotMood.CALM;
    }
}
