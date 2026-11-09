package to.sparkapp.app.ui.tutorial;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import to.sparkapp.app.config.AiConfiguration;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.config.TestPreferences;
import to.sparkapp.app.support.JavaFxTestRuntime;
import to.sparkapp.app.ui.topbar.TopBarArea;
import to.sparkapp.app.ui.webview.FxWebViewPane;
import to.sparkapp.app.windows.WindowController;

@Tag("native")
@Timeout(40)
class SparkTutorialTest {
    @TempDir
    Path directory;

    private final AtomicInteger shortcutRequests = new AtomicInteger();
    private AppPreferences preferences;
    private Stage stage;
    private WindowController controller;
    private FxWebViewPane browser;
    private TopBarArea bar;
    private SparkTutorial tutorial;

    @Test
    void mascotGuidesThroughThreeStepsAndTheTourIsRemembered() throws Exception {
        open(true, true);

        try {
            call(() -> {
                tutorial.start();

                return null;
            });

            assertEquals("Your sites", call(() -> tutorial.bubble().titleText()));
            assertEquals("Skip tutorial", call(() -> tutorial.bubble().secondaryText()));
            assertTrue(call(() -> tutorial.guide().isShowing()), "The mascot leaves the bar to guide");
            assertEquals(0, call(() -> bar.getMascot().getOpacity()));
            assertGuideBesideBubble();

            press(true);
            assertEquals("Window", call(() -> tutorial.bubble().titleText()));
            assertGuideBesideBubble();

            press(true);
            assertEquals("Open Spark from anywhere", call(() -> tutorial.bubble().titleText()));
            assertEquals("Set shortcut", call(() -> tutorial.bubble().primaryText()));

            press(false);

            assertFalse(call(tutorial::isRunning));
            assertFalse(call(() -> tutorial.bubble().isShowing()));
            assertFalse(call(() -> tutorial.guide().isShowing()));
            assertEquals(1, call(() -> bar.getMascot().getOpacity()), "The mascot returns to the bar");
            assertFalse(preferences.isTutorialPending());
            assertEquals(0, shortcutRequests.get());
        } finally {
            close();
        }
    }

    @Test
    void setShortcutFinishesTheTourAndOpensSettings() throws Exception {
        open(true, true);

        try {
            call(() -> {
                tutorial.start();

                return null;
            });
            press(true);
            press(true);
            press(true);

            assertEquals(1, shortcutRequests.get());
            assertFalse(preferences.isTutorialPending());
        } finally {
            close();
        }
    }

    @Test
    void hidingSparkMidwayKeepsTheTourForLater() throws Exception {
        open(true, true);

        try {
            call(() -> {
                tutorial.start();
                tutorial.cancel();

                return null;
            });

            assertFalse(call(() -> tutorial.guide().isShowing()));
            assertEquals(1, call(() -> bar.getMascot().getOpacity()));
            assertTrue(preferences.isTutorialPending());
        } finally {
            close();
        }
    }

    @Test
    void withoutTheMascotOnlyTheBubblesShow() throws Exception {
        open(false, true);

        try {
            call(() -> {
                tutorial.start();

                return null;
            });

            assertTrue(call(() -> tutorial.bubble().isShowing()));
            assertFalse(call(() -> tutorial.guide().isShowing()));

            press(false);
            assertFalse(preferences.isTutorialPending());
        } finally {
            close();
        }
    }

    @Test
    void guideFliesToTheBubble() throws Exception {
        open(true, false);

        try {
            call(() -> {
                tutorial.start();

                return null;
            });
            Thread.sleep(150);
            assertTrue(call(() -> tutorial.guide().isShowing()));

            Thread.sleep(700);
            assertGuideBesideBubble();
        } finally {
            close();
        }
    }

    private void open(boolean mascot, boolean reducedMotion) throws Exception {
        call(() -> {
            preferences = TestPreferences.at(directory.resolve("settings.json").toFile());
            preferences.setMascotEnabled(mascot);
            preferences.setReducedMotion(reducedMotion);
            stage = new Stage();
            controller = new WindowController(stage, preferences);
            browser = new FxWebViewPane("about:blank", preferences);
            bar = new TopBarArea(new AiConfiguration(preferences), browser, stage, preferences, () -> {
            }, () -> {
            }, controller, () -> {
            });
            var scene = new Scene(bar, 860, TopBarArea.HEIGHT);
            scene.getStylesheets().add(getClass().getResource("/css/shell.css").toExternalForm());
            stage.setScene(scene);
            // Popups are kept on screen, so the window must be on screen too.
            var screen = Screen.getPrimary().getVisualBounds();
            stage.setX(screen.getMinX() + 40);
            stage.setY(screen.getMinY() + 40);
            stage.show();
            bar.applyCss();
            bar.layout();
            tutorial = new SparkTutorial(stage, preferences, () -> bar, shortcutRequests::incrementAndGet);

            return null;
        });
    }

    private void close() throws Exception {
        call(() -> {
            tutorial.cancel();
            bar.dispose();
            controller.dispose();
            browser.shutdown(() -> {
            });
            stage.close();

            return null;
        });
    }

    private void press(boolean primary) throws Exception {
        call(() -> {
            if (primary) {
                tutorial.bubble().pressPrimary();
            } else {
                tutorial.bubble().pressSecondary();
            }

            return null;
        });
    }

    private void assertGuideBesideBubble() throws Exception {
        var card = call(() -> tutorial.bubble().cardBounds());
        var guideX = call(() -> tutorial.guide().getX());

        assertTrue(guideX + 40 <= card.getMinX() || guideX >= card.getMaxX(),
                "The guide stands next to the bubble, not on it");
        assertEquals(card.getMinY() + 10, call(() -> tutorial.guide().getY()), 1);
    }

    private static <T> T call(Callable<T> action) throws Exception {
        return JavaFxTestRuntime.call(action);
    }
}
