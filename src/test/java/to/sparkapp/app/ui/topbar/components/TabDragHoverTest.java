package to.sparkapp.app.ui.topbar.components;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.stream.Stream;

import javafx.event.EventType;
import javafx.scene.Scene;
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import to.sparkapp.app.config.AiConfiguration.AiConfig;
import to.sparkapp.app.config.TestPreferences;
import to.sparkapp.app.support.JavaFxTestRuntime;
import to.sparkapp.app.ui.webview.FxWebViewPane;

@Tag("native")
@Timeout(20)
class TabDragHoverTest {
    @TempDir
    Path directory;

    @Test
    void aFileRestingOnATabOpensItsSiteButOneCarriedPastDoesNot() throws Exception {
        var preferences = TestPreferences.at(directory.resolve("settings.json").toFile());
        var browser = JavaFxTestRuntime.call(() -> new FxWebViewPane("about:blank", preferences));
        var sites = Stream.of("one", "two", "three")
                .map(id -> new AiConfig(id, id, "https://example.com/" + id, null, null))
                .toList();
        var dock = JavaFxTestRuntime.call(() -> new AiDock(sites, browser, preferences, "one"));
        var waitMillis = (long) TabDragHover.DELAY.toMillis() + 300;

        try {
            JavaFxTestRuntime.call(() -> {
                var carriedPast = dock.getDockItems().get(2);
                carriedPast.fireEvent(drag(DragEvent.DRAG_ENTERED));
                carriedPast.fireEvent(drag(DragEvent.DRAG_EXITED));

                return null;
            });
            Thread.sleep(waitMillis);

            assertEquals("one", JavaFxTestRuntime.call(dock::getSelectedId), "A file carried past changes nothing");

            JavaFxTestRuntime.call(() -> {
                dock.getDockItems().get(1).fireEvent(drag(DragEvent.DRAG_ENTERED));

                return null;
            });
            Thread.sleep(waitMillis);

            assertEquals("two", JavaFxTestRuntime.call(dock::getSelectedId), "A file resting on a tab opens its site");
        } finally {
            JavaFxTestRuntime.call(() -> {
                dock.dispose();
                browser.shutdown(() -> {
                });

                return null;
            });
        }
    }

    @Test
    void aFileNearTheEdgeOfTheTabsScrollsThemUntilItLeaves() throws Exception {
        var preferences = TestPreferences.at(directory.resolve("settings.json").toFile());
        var stage = JavaFxTestRuntime.call(Stage::new);
        var browser = JavaFxTestRuntime.call(() -> new FxWebViewPane("about:blank", preferences));
        var sites = Stream.of("one", "two", "three", "four", "five", "six", "seven", "eight")
                .map(id -> new AiConfig(id, "Site " + id, "https://example.com/" + id, null, null))
                .toList();
        var dock = JavaFxTestRuntime.call(() -> {
            var created = new AiDock(sites, browser, preferences, "eight");
            created.setPrefWidth(260);
            created.setMaxWidth(260);
            stage.setScene(new Scene(new HBox(created), 320, 60));
            stage.show();

            return created;
        });

        try {
            Thread.sleep(300);
            var start = JavaFxTestRuntime.call(() -> {
                var scroll = dock.getScrollPane();
                var leftEdge = scroll.localToScene(scroll.getBoundsInLocal()).getMinX() + 2;
                dock.fireEvent(drag(DragEvent.DRAG_OVER, leftEdge));

                return scroll.getHvalue();
            });
            Thread.sleep(300);
            var scrolled = JavaFxTestRuntime.call(() -> {
                dock.fireEvent(drag(DragEvent.DRAG_EXITED, 0));

                return dock.getScrollPane().getHvalue();
            });
            Thread.sleep(200);

            assertTrue(scrolled < start, "The tabs scroll towards the edge the file is held at");
            assertEquals(scrolled, JavaFxTestRuntime.call(() -> dock.getScrollPane().getHvalue()), 0.0001,
                    "Scrolling stops when the file leaves the tabs");
        } finally {
            JavaFxTestRuntime.call(() -> {
                dock.dispose();
                stage.close();
                browser.shutdown(() -> {
                });

                return null;
            });
        }
    }

    private static DragEvent drag(EventType<DragEvent> type, double sceneX) {
        return new DragEvent(type, null, sceneX, 5, sceneX, 5, TransferMode.COPY, null, null, null);
    }

    private static DragEvent drag(EventType<DragEvent> type) {
        return new DragEvent(type, null, 5, 5, 5, 5, TransferMode.COPY, null, null, null);
    }
}
