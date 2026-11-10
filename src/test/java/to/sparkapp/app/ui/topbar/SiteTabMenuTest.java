package to.sparkapp.app.ui.topbar;

import java.util.List;
import java.util.stream.Stream;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import to.sparkapp.app.config.AiConfiguration;
import to.sparkapp.app.config.AiConfiguration.AiConfig;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.config.MuteRule;
import to.sparkapp.app.support.JavaFxTestRuntime;
import to.sparkapp.app.ui.topbar.components.AiDock;
import to.sparkapp.app.ui.topbar.components.DockItemNode;
import to.sparkapp.app.ui.webview.FxWebViewPane;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("native")
@Timeout(20)
class SiteTabMenuTest {
    @Test
    void rightClickOpensTheMenuOfThatSite() throws Exception {
        JavaFxTestRuntime.call(() -> {
            var fixture = new Fixture();

            try {
                var first = fixture.tab(0);
                first.fireEvent(contextMenuRequest(first));

                assertTrue(fixture.menu.isShowing());
                assertEquals("ONE", fixture.heading());
                assertTrue(fixture.row("Move left").isDisabled());

                var second = fixture.tab(1);
                second.fireEvent(contextMenuRequest(second));

                assertTrue(fixture.menu.isShowing());
                assertEquals("TWO", fixture.heading());
                assertFalse(fixture.row("Move left").isDisabled());
                assertFalse(fixture.row("Move right").isDisabled());
                for (var action : List.of("Back", "Reload")) {
                    assertTrue(fixture.row(action).isDisabled(), "A site that never opened has no page: " + action);
                }

                assertTrue(fixture.zoomButton("Zoom in").isDisabled());
                assertEquals("100%", fixture.zoomButton("Reset zoom").getText());

                fixture.row("Move right").fire();

                assertFalse(fixture.menu.isShowing());
                assertEquals(List.of("one", "three", "two"), fixture.order());
            } finally {
                fixture.close();
            }
            return null;
        });
    }

    @Test
    void theMenuAppliesToTheClickedSiteRatherThanTheOpenOne() throws Exception {
        JavaFxTestRuntime.call(() -> {
            var fixture = new Fixture();

            try {
                fixture.menu.open(fixture.tab(2));

                assertEquals("THREE", fixture.heading());
                assertTrue(fixture.row("Move right").isDisabled());

                fixture.row("Copy link").fire();

                assertEquals("https://example.com/three", Clipboard.getSystemClipboard().getString());

                fixture.menu.open(fixture.tab(2));
                fixture.settingRow("Mute site").fire();

                assertTrue(fixture.preferences.isSiteMuted("three", MuteRule.ALWAYS));
                assertFalse(fixture.preferences.isSiteMuted("one", MuteRule.ALWAYS));
                assertTrue(fixture.menu.isShowing(), "Switching a setting keeps the menu open");

                fixture.settingRow("Mute site").fire();
                fixture.row("Move left").fire();

                assertFalse(fixture.preferences.isSiteMuted("three", MuteRule.ALWAYS));
                assertEquals(List.of("one", "three", "two"), fixture.order());
                assertEquals("one", fixture.dock.getSelectedId());
            } finally {
                fixture.close();
            }
            return null;
        });
    }

    private static ContextMenuEvent contextMenuRequest(DockItemNode tab) {
        var bounds = tab.localToScreen(tab.getBoundsInLocal());

        return new ContextMenuEvent(ContextMenuEvent.CONTEXT_MENU_REQUESTED, 4, 4, bounds.getMinX() + 4,
                bounds.getMinY() + 4, false, null);
    }

    private static final class Fixture {
        private final AppPreferences preferences = new AppPreferences();
        private final List<String> previousOrder = List.copyOf(preferences.getAiOrder());
        private final FxWebViewPane browser = new FxWebViewPane("about:blank", preferences);
        private final Stage stage = new Stage();
        private final AiDock dock;
        private final SiteTabMenu menu;

        private Fixture() {
            preferences.setAiOrder(List.of());

            var sites = Stream.of("one", "two", "three")
                    .map(id -> new AiConfig(id, id.toUpperCase(), "https://example.com/" + id, "#123456", null))
                    .toList();
            dock = new AiDock(sites, browser, preferences);
            stage.setScene(new Scene(new VBox(dock), 600, 200));
            stage.show();

            var editor = new SitesEditor(stage, new AiConfiguration(preferences), () -> {
            });
            menu = new SiteTabMenu(dock, browser, editor, preferences, () -> {
            });
            dock.setOnTabMenu(menu::open);
        }

        private DockItemNode tab(int index) {
            return dock.getDockItems().get(index);
        }

        private List<String> order() {
            return dock.getDockItems().stream().map(item -> item.getConfig().id()).toList();
        }

        private String heading() {
            return ((Label) menu.getContent().getFirst().lookup(".popover-heading")).getText();
        }

        private Button row(String text) {
            return menu.getContent().getFirst().lookupAll(".shell-row").stream()
                    .map(Button.class::cast)
                    .filter(button -> button.getText().equals(text))
                    .findFirst()
                    .orElseThrow();
        }

        private Button zoomButton(String title) {
            return menu.getContent().getFirst().lookupAll(".zoom-row .button").stream()
                    .map(Button.class::cast)
                    .filter(button -> title.equals(button.getAccessibleText()))
                    .findFirst()
                    .orElseThrow();
        }

        private Button settingRow(String title) {
            return menu.getContent().getFirst().lookupAll(".shell-row").stream()
                    .map(Button.class::cast)
                    .filter(button -> String.valueOf(button.getAccessibleText()).startsWith(title + ":"))
                    .findFirst()
                    .orElseThrow();
        }

        private void close() {
            menu.dismiss();
            dock.dispose();
            browser.shutdown(() -> {
            });
            stage.close();
            preferences.setAiOrder(previousOrder);
            preferences.flush();
        }
    }
}
