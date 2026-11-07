package to.sparkapp.app.utils;

import static org.junit.jupiter.api.Assertions.*;

import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import javafx.stage.Modality;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import to.sparkapp.app.support.JavaFxTestRuntime;

@Tag("native")
@Timeout(20)
class WindowsInteractionTest {
    @Test
    void disabledNativeOwnerKeepsSparkVisibleWithoutReadingThePageAddress() throws Exception {
        var title = "Spark modal-owner test " + System.nanoTime();
        var stage = JavaFxTestRuntime.call(() -> {
            var window = new Stage();
            window.setTitle(title);
            window.setScene(new Scene(new Pane(), 200, 100));
            window.setX(-20000);
            window.setY(-20000);
            window.show();

            return window;
        });
        var modal = JavaFxTestRuntime.call(() -> {
            var window = new Stage();
            window.initOwner(stage);
            window.initModality(Modality.WINDOW_MODAL);
            window.setScene(new Scene(new Pane(), 100, 80));
            window.setX(-20000);
            window.setY(-20000);
            window.show();

            return window;
        });

        try {
            assertTrue(WindowsInteraction.hasModalWindow());
            assertTrue(NativeWindowUtils.isSparkInteractionActive());
        } finally {
            JavaFxTestRuntime.call(() -> {
                modal.close();
                stage.close();

                return null;
            });
        }
    }
}
