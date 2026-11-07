package to.sparkapp.app.ui.topbar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Button;
import javafx.scene.paint.Color;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import to.sparkapp.app.config.AiConfiguration;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.support.JavaFxTestRuntime;
import to.sparkapp.app.ui.topbar.components.SparkMascot;
import to.sparkapp.app.ui.topbar.components.MascotReaction;
import to.sparkapp.app.ui.webview.FxWebViewPane;
import to.sparkapp.app.windows.WindowController;

@Tag("native")
@Timeout(30)
class MascotShellActionsTest {
    @Test
    void actualShellActionsAnimateTheMascotAndReducedMotionResetsEveryTransform() throws Exception {
        var settingsOpened = new AtomicInteger();
        var preferences = JavaFxTestRuntime.call(AppPreferences::new);
        boolean previousMotion = preferences.isReducedMotion();
        boolean previousMascot = preferences.isMascotEnabled();
        var fixture = JavaFxTestRuntime.call(() -> {
            preferences.setReducedMotion(false);
            preferences.setMascotEnabled(true);
            var stage = new Stage();
            var controller = new WindowController(stage, preferences);
            var browser = new FxWebViewPane("about:blank", preferences);
            var bar = new TopBarArea(new AiConfiguration(preferences), browser, stage, preferences,
                    settingsOpened::incrementAndGet, () -> {}, controller, () -> {});
            var scene = new Scene(bar, 860, 52);
            scene.getStylesheets().add(getClass().getResource("/css/shell.css").toExternalForm());
            stage.setScene(scene);
            stage.setX(-20000);
            stage.setY(-20000);
            stage.show();
            var mascot = (SparkMascot) bar.getChildren().getFirst();

            return new Fixture(stage, controller, browser, bar, mascot, (Group) mascot.getGraphic());
        });

        try {
            await(() -> fixture.face().getRotate() == 0);
            fx(() -> button(fixture.bar(), "All sites").fire());
            await(() -> fixture.face().getRotate() > 1);
            fx(fixture.bar()::closeMenus);
            await(() -> fixture.face().getRotate() == 0);
            fx(() -> button(fixture.bar(), "Window options").fire());
            fx(() -> Window.getWindows().stream().filter(window -> window instanceof Popup && window.isShowing())
                    .map(window -> (Popup) window).flatMap(popup -> popup.getContent().stream())
                    .flatMap(root -> root.lookupAll(".button").stream()).filter(Button.class::isInstance)
                    .map(Button.class::cast).filter(button -> "Settings".equals(button.getText()))
                    .findFirst().orElseThrow().fire());
            var gears = JavaFxTestRuntime.call(() -> (Group) fixture.face().lookup(".mascot-gears"));
            await(() -> gears.getOpacity() > 0.9 && gears.getChildren().getFirst().getRotate() > 5);
            assertEquals(1, settingsOpened.get());
            fx(() -> {
                assertEquals(0, fixture.face().getRotate());
                assertEquals(0, fixture.face().getTranslateY());
                assertEquals(-gears.getChildren().getFirst().getRotate(),
                        gears.getChildren().getLast().getRotate());
            });
            snapshot(fixture.bar(), "settings");
            await(() -> gears.getOpacity() == 0);
            fx(() -> assertEquals(1, fixture.face().getChildren().getLast().getOpacity()));
            fx(() -> fixture.bar().getDock().selectItem(fixture.bar().getDock().getDockItems().stream()
                    .filter(item -> item != fixture.bar().getDock().getSelectedNode()).findFirst().orElseThrow()));
            await(() -> ((Group) fixture.face().getChildren().getLast()).getScaleY() < 0.5);
            fx(() -> {
                assertEquals(0, fixture.face().getTranslateX());
                assertEquals(0, fixture.face().getRotate());
                assertTrue(fixture.face().getTranslateY() > 0);
            });
            await(() -> fixture.face().getTranslateY() == 0);
            fx(() -> fixture.stage().setWidth(fixture.stage().getWidth() + 50));
            await(() -> fixture.face().getScaleX() > 1.01);
            fx(() -> fixture.mascot().react(MascotReaction.SETTINGS));
            await(() -> gears.getOpacity() > 0.9);
            fx(() -> {
                preferences.setReducedMotion(true);
                fixture.bar().refreshAppearance();
                fixture.mascot().fire();
                fixture.stage().setHeight(fixture.stage().getHeight() + 30);
                assertEquals(1, fixture.face().getScaleX());
                assertEquals(1, fixture.face().getScaleY());
                assertEquals(0, fixture.face().getRotate());
                assertEquals(0, fixture.face().getTranslateX());
                assertEquals(0, fixture.face().getTranslateY());
                assertEquals(0, gears.getOpacity());
                assertEquals(1, fixture.face().getChildren().getLast().getOpacity());
                fixture.mascot().requestFocus();
                fixture.bar().applyCss();
                assertTrue(fixture.mascot().getBorder() == null || fixture.mascot().getBorder().getStrokes().stream()
                        .allMatch(stroke -> stroke.getWidths().getTop() == 0));
            });
            snapshot(fixture.bar(), "focused");
        } finally {
            fx(() -> {
                fixture.bar().dispose();
                fixture.controller().dispose();
                fixture.browser().shutdown(() -> {});
                fixture.stage().close();
                preferences.setReducedMotion(previousMotion);
                preferences.setMascotEnabled(previousMascot);
                preferences.flush();
            });
        }
    }

    private void snapshot(TopBarArea bar, String name) throws Exception {
        JavaFxTestRuntime.call(() -> {
            var directory = Path.of("build", "mascot-review");
            Files.createDirectories(directory);

            for (var width : new int[]{860, 700}) {
                bar.resize(width, 52);
                bar.applyCss();
                bar.layout();
                var parameters = new SnapshotParameters();
                parameters.setFill(Color.web("#25262a"));
                var image = bar.snapshot(parameters, null);
                ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png",
                        directory.resolve(name + "-" + width + ".png").toFile());
            }

            return null;
        });
    }

    private Button button(TopBarArea bar, String title) {
        return bar.getChildren().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> title.equals(button.getAccessibleText())).findFirst().orElseThrow();
    }

    private void fx(Runnable action) throws Exception {
        JavaFxTestRuntime.call(() -> {
            action.run();

            return null;
        });
    }

    private void await(BooleanSupplier condition) throws Exception {
        var deadline = System.nanoTime() + 3_000_000_000L;

        while (System.nanoTime() < deadline) {
            if (JavaFxTestRuntime.call(condition::getAsBoolean)) {
                return;
            }

            Thread.sleep(15);
        }

        throw new AssertionError("The shell action did not produce its expected reaction");
    }

    private record Fixture(Stage stage, WindowController controller, FxWebViewPane browser, TopBarArea bar,
                           SparkMascot mascot, Group face) {
    }
}
