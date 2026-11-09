package to.sparkapp.app.ui.topbar.components;

import java.util.function.BooleanSupplier;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableBooleanValue;

/**
 * Plays the mascot's short reactions and keeps its mood between them. A reaction briefly replaces
 * the mood, which returns once the reaction ends.
 */
final class MascotExpressions {
    private final BooleanSupplier animated;
    private final MascotMotion motion;
    private final MascotMoodMotion moodMotion;
    private final MascotMoods moods = new MascotMoods(this::moodChanged);
    private final ChangeListener<Boolean> loading = (observable, before, value) -> moods.setLoading(value);
    private final ChangeListener<Boolean> failed = (observable, before, value) -> moods.setFailed(value);
    private ObservableBooleanValue pageLoading;
    private ObservableBooleanValue pageFailed;

    /**
     * @param animated whether the mascot may move now
     */
    MascotExpressions(MascotFace face, BooleanSupplier animated) {
        this.animated = animated;
        motion = new MascotMotion(face, this::settled);
        moodMotion = new MascotMoodMotion(face);
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
        if (animated.getAsBoolean() && moods.wake()) {
            play(MascotReaction.WAKE);
        }
    }

    boolean isAsleep() {
        return moods.isAsleep();
    }

    void setActive(boolean active) {
        moods.setActive(active);
    }

    void reset() {
        motion.reset();
        moodMotion.clear();
    }

    void dispose() {
        reset();
        moods.dispose();

        if (pageLoading != null) {
            pageLoading.removeListener(loading);
            pageFailed.removeListener(failed);
        }
    }

    private void play(MascotReaction expression) {
        moodMotion.clear();
        motion.play(expression);
    }

    private void settled() {
        if (animated.getAsBoolean()) {
            moodMotion.show(moods.getMood());
        }
    }

    private void moodChanged(MascotMood mood) {
        if (animated.getAsBoolean() && !motion.isPlaying()) {
            moodMotion.show(mood);
        }
    }
}
