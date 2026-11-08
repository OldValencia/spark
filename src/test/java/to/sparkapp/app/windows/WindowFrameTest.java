package to.sparkapp.app.windows;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicBoolean;

import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import to.sparkapp.app.support.JavaFxTestRuntime;

@Tag("native")
@Timeout(20)
class WindowFrameTest {
    private static final Color SITE = Color.web("#74aa9c");

    @Test
    void hiddenWindowTakesTheSiteColorAtOnce() throws Exception {
        var shell = JavaFxTestRuntime.call(StackPane::new);
        var frame = JavaFxTestRuntime.call(() -> new WindowFrame(shell, () -> false));

        JavaFxTestRuntime.call(() -> {
            frame.showAccent(SITE);

            return null;
        });

        assertEquals(WindowFrame.outlineFor(SITE), frame.getOutline());
        assertTrue(shell.getStyle().contains(hex(WindowFrame.outlineFor(SITE))));
    }

    @Test
    void visibleWindowFadesToTheSiteColorUnlessMotionIsReduced() throws Exception {
        var reducedMotion = new AtomicBoolean();
        var shell = JavaFxTestRuntime.call(StackPane::new);
        var stage = JavaFxTestRuntime.call(() -> showOffScreen(shell));
        var frame = JavaFxTestRuntime.call(() -> new WindowFrame(shell, reducedMotion::get));

        try {
            JavaFxTestRuntime.call(() -> {
                frame.showAccent(SITE);

                return null;
            });
            assertNotEquals(WindowFrame.outlineFor(SITE), frame.getOutline(), "The color must fade in, not jump");
            Thread.sleep(400);
            assertEquals(WindowFrame.outlineFor(SITE), JavaFxTestRuntime.call(frame::getOutline));

            reducedMotion.set(true);
            var other = Color.web("#D97757");
            JavaFxTestRuntime.call(() -> {
                frame.showAccent(other);

                return null;
            });
            assertEquals(WindowFrame.outlineFor(other), frame.getOutline());
        } finally {
            JavaFxTestRuntime.call(() -> {
                stage.close();

                return null;
            });
        }
    }

    @Test
    void dockedWindowKeepsOnlyTheLowerCornersRounded() throws Exception {
        var shell = JavaFxTestRuntime.call(StackPane::new);
        var frame = JavaFxTestRuntime.call(() -> new WindowFrame(shell, () -> false));

        JavaFxTestRuntime.call(() -> {
            frame.setDocked(true);

            return null;
        });
        assertTrue(shell.getStyle().contains("-fx-border-radius:0 0 16 16;"));

        JavaFxTestRuntime.call(() -> {
            frame.setDocked(false);

            return null;
        });
        assertTrue(shell.getStyle().contains("-fx-border-radius:16;"));
    }

    private static Stage showOffScreen(StackPane shell) {
        var stage = new Stage();
        stage.setScene(new Scene(shell, 200, 120));
        stage.setX(-20000);
        stage.setY(-20000);
        stage.show();

        return stage;
    }

    private static String hex(Color color) {
        return String.format("#%02X%02X%02X", (int) (color.getRed() * 255), (int) (color.getGreen() * 255),
                (int) (color.getBlue() * 255));
    }
}
