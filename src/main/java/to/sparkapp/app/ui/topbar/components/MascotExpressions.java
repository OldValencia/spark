package to.sparkapp.app.ui.topbar.components;

import java.util.function.BooleanSupplier;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableBooleanValue;

/**
 * Plays the mascot's short reactions and keeps its mood between them. A reaction briefly replaces
 * the mood, which returns once the reaction ends; between them the mascot blinks and looks around.
 */
final class MascotExpressions {
    private final BooleanSupplier animated;
    private final Runnable lookBack;
    private final MascotMotion motion;
    private final MascotMoodMotion moodMotion;
    private final MascotMoods moods = new MascotMoods(this::moodChanged);
    private final MascotRelief relief = new MascotRelief();
    private final MascotHearing hearing = new MascotHearing(moods::setListening);
    private final MascotIdle idle;
    private final ChangeListener<Boolean> loading = (observable, before, value) -> moods.setLoading(value);
    private final ChangeListener<Boolean> failed = (observable, before, value) -> moods.setFailed(value);
    private ObservableBooleanValue pageLoading;
    private ObservableBooleanValue pageFailed;

    /**
     * @param animated whether the mascot may move now
     * @param lookBack turns the eyes back to the pointer once a glance or a reaction has moved them away
     */
    MascotExpressions(MascotFace face, BooleanSupplier animated, Runnable lookBack) {
        this.animated = animated;
        this.lookBack = lookBack;
        motion = new MascotMotion(face, this::settled);
        moodMotion = new MascotMoodMotion(face);
        idle = new MascotIdle(face, this::idleFree, lookBack);
    }

    void observePage(ObservableBooleanValue loading, ObservableBooleanValue failed) {
        pageLoading = loading;
        pageFailed = failed;
        loading.addListener(this.loading);
        failed.addListener(this.failed);
        moods.setLoading(loading.get());
        moods.setFailed(failed.get());
    }

    void react(MascotReaction expression) {
        if (!animated.getAsBoolean()) {
            return;
        }

        moods.wake();
        play(expression);
    }

    /** Any sign of the user wakes a sleeping mascot. */
    void activity() {
        idle.activity();

        if (animated.getAsBoolean() && moods.wake()) {
            play(MascotReaction.WAKE);
        }
    }

    /** Whether a site plays sound now; lasting sound makes the mascot sway. */
    void setSound(boolean playing) {
        hearing.setSound(playing);
    }

    boolean isAsleep() {
        return moods.isAsleep();
    }

    void setActive(boolean active) {
        moods.setActive(active);
        idle.setActive(active);
    }

    void reset() {
        idle.interrupt();
        motion.reset();
        moodMotion.clear();
    }

    void dispose() {
        reset();
        idle.setActive(false);
        hearing.dispose();
        moods.dispose();

        if (pageLoading != null) {
            pageLoading.removeListener(loading);
            pageFailed.removeListener(failed);
        }
    }

    private void play(MascotReaction expression) {
        idle.interrupt();
        moodMotion.clear();
        motion.play(expression);
    }

    private void settled() {
        if (animated.getAsBoolean()) {
            moodMotion.show(moods.getMood());
            lookBack.run();
        }
    }

    private void moodChanged(MascotMood mood) {
        var relieved = relief.changed(mood);

        if (!animated.getAsBoolean()) {
            return;
        }

        if (relieved) {
            play(MascotReaction.HAPPY);
        } else if (!motion.isPlaying()) {
            moodMotion.show(mood);
        }
    }

    private boolean idleFree() {
        var mood = moods.getMood();

        return animated.getAsBoolean() && !motion.isPlaying() && mood != MascotMood.THINKING
                && mood != MascotMood.ASLEEP;
    }
}
