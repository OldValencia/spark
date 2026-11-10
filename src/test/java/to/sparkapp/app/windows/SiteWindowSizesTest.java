package to.sparkapp.app.windows;

import static org.junit.jupiter.api.Assertions.*;

import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.config.SiteWindowSize;
import to.sparkapp.app.config.WindowSizePreset;
import to.sparkapp.app.config.WindowSizePreset.Slot;
import to.sparkapp.app.support.JavaFxTestRuntime;

/** Uses the isolated profile of the native test run and puts its settings back afterwards. */
@Tag("native")
@Timeout(20)
class SiteWindowSizesTest {
    private static final String INSTAGRAM = "size-test-instagram";
    private static final String CHAT = "size-test-chat";

    @Test
    void aSiteWithItsOwnSizeGetsItWheneverItIsShown() throws Exception {
        run(fixture -> {
            var window = fixture.window;
            window.showSite(CHAT);
            window.preset(800, 600);

            window.showSite(INSTAGRAM);
            window.setOwnSize(INSTAGRAM, true);
            window.preset(500, 700);

            assertTrue(window.hasOwnSize(INSTAGRAM));
            assertFalse(window.hasOwnSize(CHAT));

            fixture.moveTo(100, 100);
            window.showSite(CHAT);

            fixture.assertSize(800, 600, "A site without its own size uses the shared size");
            fixture.assertCorner(100, 100, "The tabs stay under the pointer");

            window.showSite(INSTAGRAM);

            fixture.assertSize(500, 700, "The site gets its own size back");
        });
    }

    @Test
    void sizesChosenOnASiteChangeOnlyThatSite() throws Exception {
        run(fixture -> {
            var window = fixture.window;
            window.showSite(CHAT);
            window.preset(800, 600);
            window.showSite(INSTAGRAM);
            window.setOwnSize(INSTAGRAM, true);

            window.preset(520, 720);
            window.showSite(CHAT);
            window.preset(760, 560);
            window.showSite(INSTAGRAM);

            fixture.assertSize(520, 720, "Resizing another site does not touch this one");

            window.showSite(CHAT);

            fixture.assertSize(760, 560, "The shared size follows sites without their own");
        });
    }

    @Test
    void aSiteOnAPresetFollowsThePresetUntilItIsResizedByHand() throws Exception {
        run(fixture -> {
            var window = fixture.window;
            var preferences = fixture.preferences;
            window.showSite(INSTAGRAM);
            window.setOwnSize(INSTAGRAM, true);
            window.preset(Slot.SMALL);

            assertEquals(SiteWindowSize.ofPreset(Slot.SMALL), preferences.getSiteWindowSize(INSTAGRAM));

            preferences.setWindowSizePreset(Slot.SMALL, new WindowSizePreset("S", 640, 520));
            window.showSite(CHAT);
            window.showSite(INSTAGRAM);

            fixture.assertSize(640, 520, "The site follows the changed preset");

            window.preset(600, 500);

            assertEquals(SiteWindowSize.custom(600, 500), preferences.getSiteWindowSize(INSTAGRAM));
        });
    }

    @Test
    void aSiteCanFillTheScreenAndTheNextSiteReturnsToWhereTheWindowWas() throws Exception {
        run(fixture -> {
            var window = fixture.window;
            window.showSite(CHAT);
            window.preset(800, 600);
            fixture.moveTo(120, 140);
            window.showSite(INSTAGRAM);
            window.setOwnSize(INSTAGRAM, true);

            window.fill();
            window.showSite(CHAT);

            fixture.assertSize(800, 600, "The shared size is not the screen");
            fixture.assertCorner(120, 140, "The window returns to where it stood before filling");

            window.showSite(INSTAGRAM);
            var screen = fixture.screen();

            fixture.assertSize(screen.getWidth(), screen.getHeight(), "The site fills the screen again");
        });
    }

    @Test
    void turningTheSizeOffReturnsTheSiteToTheSharedSize() throws Exception {
        run(fixture -> {
            var window = fixture.window;
            window.showSite(CHAT);
            window.preset(800, 600);
            window.showSite(INSTAGRAM);
            window.setOwnSize(INSTAGRAM, true);
            window.preset(500, 700);

            window.setOwnSize(INSTAGRAM, false);

            assertNull(fixture.preferences.getSiteWindowSize(INSTAGRAM));
            fixture.assertSize(800, 600, "The site uses the shared size again");
        });
    }

    private interface Scenario {
        void run(Fixture fixture);
    }

    private static void run(Scenario scenario) throws Exception {
        JavaFxTestRuntime.call(() -> {
            var fixture = new Fixture();

            try {
                scenario.run(fixture);
            } finally {
                fixture.close();
            }
            return null;
        });
    }

    private static final class Fixture {
        private final AppPreferences preferences = new AppPreferences();
        private final AppPreferences.WindowPlacement previousPlacement = preferences.getWindowPlacement();
        private final WindowSizePreset previousPreset = preferences.getWindowSizePreset(Slot.SMALL);
        private final Stage stage = new Stage();
        private final WindowController window;

        private Fixture() {
            stage.setScene(new Scene(new StackPane(), 400, 300));
            window = new WindowController(stage, preferences);

            if (window.isDocked()) {
                window.toggleDock();
            }
        }

        private void moveTo(double x, double y) {
            var screen = screen();
            stage.setX(screen.getMinX() + x);
            stage.setY(screen.getMinY() + y);
        }

        private Rectangle2D screen() {
            return Screen.getPrimary().getVisualBounds();
        }

        private void assertSize(double width, double height, String message) {
            assertEquals(width, stage.getWidth(), 1, message);
            assertEquals(height, stage.getHeight(), 1, message);
        }

        private void assertCorner(double x, double y, String message) {
            assertEquals(screen().getMinX() + x, stage.getX(), 1, message);
            assertEquals(screen().getMinY() + y, stage.getY(), 1, message);
        }

        private void close() {
            window.dispose();
            stage.close();
            preferences.setSiteWindowSize(INSTAGRAM, null);
            preferences.setSiteWindowSize(CHAT, null);
            preferences.setWindowSizePreset(Slot.SMALL, previousPreset);
            preferences.setWindowPlacement(previousPlacement);
            preferences.flush();
        }
    }
}
