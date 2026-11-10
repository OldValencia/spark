package to.sparkapp.app.ui.topbar.components;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.concurrent.Callable;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.config.TestPreferences;
import to.sparkapp.app.support.JavaFxTestRuntime;

@Tag("native")
@Timeout(30)
class MascotMoodFaceTest {
    @TempDir
    Path directory;

    private final SimpleBooleanProperty loading = new SimpleBooleanProperty();
    private final SimpleBooleanProperty failed = new SimpleBooleanProperty();
    private AppPreferences preferences;
    private Stage stage;
    private SparkMascot mascot;

    @Test
    void mascotThinksWhileLoadingAndLooksSadWhenTheLoadFails() throws Exception {
        open(false);

        try {
            await(() -> !reactionRunning());
            set(loading, true);
            await(() -> gears().getOpacity() > 0.95);
            var before = call(() -> gears().getChildren().getFirst().getRotate());
            Thread.sleep(300);
            assertNotEquals(before, call(() -> gears().getChildren().getFirst().getRotate()), "Gears must turn");

            set(loading, false);
            set(failed, true);
            await(() -> mouth().getOpacity() > 0.95 && gears().getOpacity() < 0.05);
            assertTrue(call(() -> eyes().getScaleY()) < 0.8, "Sad eyes narrow");

            set(failed, false);
            await(() -> mouth().getOpacity() < 0.05 && call(() -> eyes().getScaleY()) > 0.99);
        } finally {
            close();
        }
    }

    @Test
    void mascotBeamsWhenHappyAndSeesSpiralsWhenDizzy() throws Exception {
        open(false);

        try {
            await(() -> !reactionRunning());
            call(() -> {
                mascot.react(MascotReaction.HAPPY);

                return null;
            });
            await(() -> call(() -> eyes().getOpacity()) < 0.05 && call(() -> features().getChildren().getFirst()
                    .getOpacity()) > 0.95);
            await(() -> call(() -> eyes().getOpacity()) > 0.99);

            call(() -> {
                mascot.react(MascotReaction.DIZZY);

                return null;
            });
            await(() -> call(() -> features().getChildren().getLast().getOpacity()) > 0.95);
            await(() -> call(() -> eyes().getOpacity()) > 0.99 && call(() -> face().getRotate()) == 0);
        } finally {
            close();
        }
    }

    @Test
    void reducedMotionKeepsTheMascotStill() throws Exception {
        open(true);

        try {
            set(loading, true);
            set(failed, true);
            Thread.sleep(600);

            assertEquals(0, call(() -> gears().getOpacity()));
            assertEquals(0, call(() -> mouth().getOpacity()));
            assertEquals(1, call(() -> eyes().getScaleY()));
        } finally {
            close();
        }
    }

    private void open(boolean reducedMotion) throws Exception {
        call(() -> {
            preferences = TestPreferences.at(directory.resolve("settings.json").toFile());
            preferences.setMascotEnabled(true);
            preferences.setReducedMotion(reducedMotion);
            stage = new Stage();
            mascot = new SparkMascot(stage, preferences);
            mascot.observePage(loading, failed);
            stage.setScene(new Scene(new StackPane(mascot), 120, 60));
            stage.setX(-20000);
            stage.setY(-20000);
            stage.show();

            return null;
        });
    }

    private void close() throws Exception {
        call(() -> {
            mascot.dispose();
            stage.close();

            return null;
        });
    }

    private boolean reactionRunning() throws Exception {
        return call(() -> face().getTranslateY() != 0 || face().getScaleY() != 1);
    }

    private Group face() {
        return (Group) mascot.getGraphic();
    }

    private Group gears() {
        return (Group) face().lookup(".mascot-gears");
    }

    private Node mouth() {
        return face().getChildren().get(4);
    }

    private Group features() {
        return (Group) face().getChildren().get(face().getChildren().size() - 2);
    }

    private Node eyes() {
        return face().getChildren().getLast();
    }

    private static void set(SimpleBooleanProperty property, boolean value) throws Exception {
        call(() -> {
            property.set(value);

            return null;
        });
    }

    private static <T> T call(Callable<T> action) throws Exception {
        return JavaFxTestRuntime.call(action);
    }

    private static void await(Callable<Boolean> condition) throws Exception {
        var until = System.nanoTime() + 5_000_000_000L;

        while (!condition.call() && System.nanoTime() < until) {
            Thread.sleep(20);
        }

        assertTrue(condition.call(), "Timed out waiting for the mascot");
    }
}
