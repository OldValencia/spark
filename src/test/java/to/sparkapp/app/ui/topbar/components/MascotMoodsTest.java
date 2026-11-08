package to.sparkapp.app.ui.topbar.components;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javafx.util.Duration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import to.sparkapp.app.support.JavaFxTestRuntime;

@Tag("native")
@Timeout(20)
class MascotMoodsTest {
    private final List<MascotMood> changes = new CopyOnWriteArrayList<>();

    @Test
    void thinksOnlyWhenLoadingTakesAMoment() throws Exception {
        var moods = create(Duration.minutes(5));

        fx(() -> moods.setLoading(true));
        Thread.sleep(100);
        fx(() -> moods.setLoading(false));
        Thread.sleep(400);
        assertTrue(changes.isEmpty(), "A quick load must not change the mood");

        fx(() -> moods.setLoading(true));
        Thread.sleep(500);
        assertEquals(MascotMood.THINKING, mood(moods));

        fx(() -> moods.setLoading(false));
        assertEquals(MascotMood.CALM, mood(moods));
    }

    @Test
    void failureOutweighsEverythingElse() throws Exception {
        var moods = create(Duration.minutes(5));

        fx(() -> moods.setFailed(true));
        fx(() -> moods.setLoading(true));
        Thread.sleep(500);

        assertEquals(MascotMood.SAD, mood(moods));
    }

    @Test
    void fallsAsleepWhenNothingHappensAndWakesOnActivity() throws Exception {
        var moods = create(Duration.millis(300));

        Thread.sleep(600);
        assertEquals(MascotMood.ASLEEP, mood(moods));

        assertTrue(JavaFxTestRuntime.call(moods::wake), "Waking reports that the mascot slept");
        assertEquals(MascotMood.CALM, mood(moods));
        assertFalse(JavaFxTestRuntime.call(moods::wake));
    }

    @Test
    void hiddenMascotKeepsCalmAndDoesNotCountDown() throws Exception {
        var moods = create(Duration.millis(300));

        fx(() -> moods.setActive(false));
        fx(() -> moods.setFailed(true));
        Thread.sleep(600);

        assertEquals(MascotMood.CALM, mood(moods));
        assertFalse(JavaFxTestRuntime.call(moods::isAsleep));
    }

    private MascotMoods create(Duration sleepAfter) throws Exception {
        return JavaFxTestRuntime.call(() -> {
            var moods = new MascotMoods(changes::add, sleepAfter);
            moods.setActive(true);

            return moods;
        });
    }

    private static MascotMood mood(MascotMoods moods) throws Exception {
        return JavaFxTestRuntime.call(moods::getMood);
    }

    private static void fx(Runnable action) throws Exception {
        JavaFxTestRuntime.call(() -> {
            action.run();

            return null;
        });
    }
}
