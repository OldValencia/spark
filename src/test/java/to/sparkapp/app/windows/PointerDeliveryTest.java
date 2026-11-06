package to.sparkapp.app.windows;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javafx.geometry.Point2D;
import javafx.scene.robot.Robot;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import to.sparkapp.app.support.JavaFxTestRuntime;

@Tag("native")
@Timeout(20)
class PointerDeliveryTest {
    @Test
    void aShortNativeBurstSurvivesJavaFxPointerCoalescing() throws Exception {
        var opened = new CompletableFuture<Void>();
        var fixture = JavaFxTestRuntime.call(() -> {
            var pill = new QuickAccessPillFixture(true, true);
            var controller = new WindowController(pill.stage, pill.preferences);
            controller.setOnReveal(() -> opened.complete(null));
            var autoHide = new WindowAutoHide(pill.stage, pill.preferences, controller, () -> false, () -> {
            });
            var robot = new Robot();
            var previousPointer = robot.getMousePosition();
            pill.stage.show();
            robot.mouseMove(pill.centerX(), pill.edgeY() + 200);
            pill.stage.hide();
            robot.mouseMove(pill.centerX(), pill.edgeY());
            return new Delivery(pill, controller, autoHide, previousPointer);
        });

        try {
            var start = System.nanoTime() - 120_000_000;
            var scale = fixture.pill().screen.getOutputScaleY();
            for (var step = 0; step <= 100; step++) {
                fixture.autoHide().onGlobalMouseMoved(500, (300 - step * 3) * scale, start + step * 1_000_000L);
            }
            opened.get(3, TimeUnit.SECONDS);
        } finally {
            JavaFxTestRuntime.call(() -> {
                fixture.controller().dispose();
                fixture.autoHide().cancel();
                new Robot().mouseMove(fixture.previousPointer());
                fixture.pill().close();
                return null;
            });
        }
    }

    private record Delivery(QuickAccessPillFixture pill, WindowController controller, WindowAutoHide autoHide,
                            Point2D previousPointer) {
    }
}
