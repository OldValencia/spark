package to.sparkapp.app.ui.topbar.components;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.stream.Stream;

import javafx.geometry.Bounds;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import to.sparkapp.app.config.AiConfiguration.AiConfig;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.config.TestPreferences;
import to.sparkapp.app.support.JavaFxTestRuntime;
import to.sparkapp.app.ui.webview.FxWebViewPane;

@Tag("native")
@Timeout(30)
class TabSelectionIndicatorTest {
    @TempDir
    Path directory;

    @Test
    void highlightSlidesToTheSelectedTabAndFollowsIt() throws Exception {
        var fixture = JavaFxTestRuntime.call(() -> new Fixture(directory, false));

        try {
            settle();
            assertMatches(fixture, 0);

            select(fixture, 2);
            Thread.sleep(60);
            var midway = JavaFxTestRuntime.call(() -> fixture.indicator().highlightBounds());
            var first = JavaFxTestRuntime.call(() -> fixture.tab(0).getBoundsInParent());
            var third = JavaFxTestRuntime.call(() -> fixture.tab(2).getBoundsInParent());
            assertTrue(midway.getMinX() > first.getMinX() && midway.getMinX() < third.getMinX(),
                    "The highlight must travel between the tabs");

            Thread.sleep(400);
            assertMatches(fixture, 2);

            JavaFxTestRuntime.call(() -> {
                fixture.tab(2).setTranslateX(-30);

                return null;
            });
            assertMatches(fixture, 2);
        } finally {
            JavaFxTestRuntime.call(fixture::close);
        }
    }

    @Test
    void reducedMotionMovesTheHighlightAtOnce() throws Exception {
        var fixture = JavaFxTestRuntime.call(() -> new Fixture(directory, true));

        try {
            settle();
            select(fixture, 1);

            assertMatches(fixture, 1);
        } finally {
            JavaFxTestRuntime.call(fixture::close);
        }
    }

    private static void select(Fixture fixture, int index) throws Exception {
        JavaFxTestRuntime.call(() -> {
            fixture.dock.selectItem(fixture.tab(index));

            return null;
        });
    }

    private static void assertMatches(Fixture fixture, int index) throws Exception {
        Bounds highlight = JavaFxTestRuntime.call(() -> fixture.indicator().highlightBounds());
        Bounds tab = JavaFxTestRuntime.call(() -> fixture.tab(index).getBoundsInParent());

        assertNotNull(highlight);
        assertEquals(tab.getMinX(), highlight.getMinX(), 0.5);
        assertEquals(tab.getMinY(), highlight.getMinY(), 0.5);
        assertEquals(tab.getWidth(), highlight.getWidth(), 0.5);
        assertEquals(tab.getHeight(), highlight.getHeight(), 0.5);
    }

    /** Lets JavaFX lay out the shown stage. */
    private static void settle() throws Exception {
        Thread.sleep(150);
        JavaFxTestRuntime.call(() -> null);
    }

    private static final class Fixture {
        private final AppPreferences preferences;
        private final FxWebViewPane browser;
        private final Stage stage = new Stage();
        private final AiDock dock;

        private Fixture(Path directory, boolean reducedMotion) {
            preferences = TestPreferences.at(directory.resolve("settings.json").toFile());
            preferences.setReducedMotion(reducedMotion);
            browser = new FxWebViewPane("about:blank", preferences);

            var sites = Stream.of("#74aa9c", "#4E86F8", "#D97757")
                    .map(color -> new AiConfig(color, "Site " + color, "https://example.com/" + color.substring(1),
                            color, null))
                    .toList();
            dock = new AiDock(sites, browser, preferences);
            stage.setScene(new Scene(new VBox(dock), 700, 120));
            stage.setX(-20000);
            stage.setY(-20000);
            stage.show();
        }

        private DockItemNode tab(int index) {
            return (DockItemNode) dock.getDockContainer().getChildren().get(index);
        }

        private TabSelectionIndicator indicator() {
            var content = (StackPane) dock.getScrollPane().getContent();

            return (TabSelectionIndicator) content.getChildren().getFirst();
        }

        private Void close() {
            dock.dispose();
            stage.close();
            browser.shutdown(() -> {
            });

            return null;
        }
    }
}
