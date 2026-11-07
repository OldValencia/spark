package to.sparkapp.app.ui.topbar;

import java.awt.Robot;
import java.awt.event.InputEvent;
import java.util.List;

import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import to.sparkapp.app.config.AiConfiguration;
import to.sparkapp.app.config.AiConfiguration.AiConfig;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.support.JavaFxTestRuntime;
import to.sparkapp.app.ui.topbar.components.AiDock;
import to.sparkapp.app.ui.webview.FxWebViewPane;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("native")
@Tag("flaky")
@Timeout(30)
class SiteListReorderTest {
    @Test
    void movingTheLastSiteUpAndBackDownKeepsAllRowsVisible() throws Exception {
        var fixture = JavaFxTestRuntime.call(Fixture::new);

        try {
            Thread.sleep(250);
            drag(fixture, 3, 2, false);
            fixture.assertOrder(List.of("one", "two", "four", "three"));
            drag(fixture, 2, 3, true);
            fixture.assertOrder(List.of("one", "two", "three", "four"));
        } finally {
            JavaFxTestRuntime.call(() -> {
                fixture.popup.hide();
                fixture.dock.dispose();
                fixture.browser.shutdown(() -> {
                });
                fixture.stage.close();
                fixture.preferences.setAiOrder(fixture.previousOrder);
                fixture.preferences.flush();
                return null;
            });
        }
    }

    private void drag(Fixture fixture, int from, int to, boolean after) throws Exception {
        var start = JavaFxTestRuntime.call(() -> {
            var grip = fixture.cell(from).getGraphic().lookup(".shell-button");
            var bounds = grip.localToScreen(grip.getBoundsInLocal());
            return new Point2D(bounds.getCenterX(), bounds.getCenterY());
        });
        var end = JavaFxTestRuntime.call(() -> {
            var cell = fixture.cell(to);
            var bounds = cell.localToScreen(cell.getBoundsInLocal());
            return new Point2D(start.getX(), after ? bounds.getMaxY() - 8 : bounds.getMinY() + 8);
        });
        var robot = new Robot();
        robot.mouseMove((int) start.getX(), (int) start.getY());
        Thread.sleep(100);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);

        try {
            Thread.sleep(100);

            for (var step = 1; step <= 15; step++) {
                var point = start.add(end.subtract(start).multiply(step / 15.0));
                robot.mouseMove((int) point.getX(), (int) point.getY());
                Thread.sleep(25);
            }
        } finally {
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        }

        Thread.sleep(250);
    }

    private static final class Fixture {
        private final AppPreferences preferences = new AppPreferences();
        private final List<String> previousOrder = List.copyOf(preferences.getAiOrder());
        private final FxWebViewPane browser = new FxWebViewPane("about:blank", preferences);
        private final Stage stage = new Stage();
        private final AiDock dock;
        private final SitesPopup popup;

        private Fixture() {
            preferences.setAiOrder(List.of());
            var sites = List.of("one", "two", "three", "four").stream()
                    .map(id -> new AiConfig(id, id, "https://example.com/" + id, "#123456", null)).toList();
            dock = new AiDock(sites, browser, preferences);
            var anchor = new Button("All sites");
            stage.setScene(new Scene(new VBox(dock, anchor), 700, 450));
            stage.setAlwaysOnTop(true);
            stage.show();
            stage.getScene().getRoot().applyCss();
            stage.getScene().getRoot().layout();
            popup = new SitesPopup(stage, dock, new AiConfiguration(preferences), preferences, () -> {
            });
            popup.open(anchor);
        }

        private SiteListCell cell(int index) {
            return popup.getContent().getFirst().lookupAll(".list-cell").stream()
                    .filter(SiteListCell.class::isInstance).map(SiteListCell.class::cast)
                    .filter(cell -> !cell.isEmpty() && cell.getIndex() == index).findFirst().orElseThrow();
        }

        private void assertOrder(List<String> expected) throws Exception {
            JavaFxTestRuntime.call(() -> {
                assertEquals(expected, dock.getDockItems().stream().map(item -> item.getConfig().id()).toList());
                var list = (ListView<?>) popup.getContent().getFirst().lookup(".list-view");
                var viewport = list.lookup(".clipped-container");
                var visibleBounds = viewport.localToScene(viewport.getBoundsInLocal());

                for (var index = 0; index < expected.size(); index++) {
                    var cell = cell(index);
                    assertEquals(expected.get(index), cell.getItem().id());
                    assertEquals(1, cell.getOpacity());
                    assertEquals(0, cell.getTranslateY());
                    assertTrue(cell.isVisible());
                    var bounds = cell.localToScene(cell.getBoundsInLocal());
                    assertTrue(bounds.getMinY() >= visibleBounds.getMinY() - 1,
                            "Row " + index + " is above the visible list: " + bounds);
                    assertTrue(bounds.getMaxY() <= visibleBounds.getMaxY() + 1,
                            "Row " + index + " is below the visible list: " + bounds);
                }

                var tabs = dock.getDockItems();

                for (var index = 1; index < tabs.size(); index++) {
                    var previous = tabs.get(index - 1).getBoundsInParent();
                    var current = tabs.get(index).getBoundsInParent();
                    assertEquals(dock.getDockContainer().getSpacing(), current.getMinX() - previous.getMaxX(), 1,
                            "Unexpected gap before tab " + index);
                }
                return null;
            });
        }
    }
}
