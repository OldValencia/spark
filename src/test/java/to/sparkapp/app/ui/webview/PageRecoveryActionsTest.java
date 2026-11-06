package to.sparkapp.app.ui.webview;

import java.util.concurrent.atomic.AtomicInteger;

import javafx.scene.control.Button;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import to.sparkapp.app.support.JavaFxTestRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("native")
class PageRecoveryActionsTest {
    @Test
    void retryEditAndShowPageKeepIndependentActions() throws Exception {
        JavaFxTestRuntime.call(() -> {
            var actions = new PageRecoveryActions();
            var retries = new AtomicInteger();
            var edits = new AtomicInteger();
            var previews = new AtomicInteger();
            actions.configure(retries::incrementAndGet, edits::incrementAndGet, previews::incrementAndGet);

            for (var node : actions.getChildren()) {
                assertTrue(node.isVisible());
                ((Button) node).fire();
            }

            assertEquals(1, retries.get());
            assertEquals(1, edits.get());
            assertEquals(1, previews.get());

            actions.configure(retries::incrementAndGet, null, null);

            for (var node : actions.getChildren().subList(1, 3)) {
                assertFalse(node.isVisible());
                assertFalse(node.isManaged());
                ((Button) node).fire();
            }

            assertEquals(1, edits.get());
            assertEquals(1, previews.get());
            return null;
        });
    }
}
