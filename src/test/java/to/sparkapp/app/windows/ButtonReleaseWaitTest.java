package to.sparkapp.app.windows;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import to.sparkapp.app.support.JavaFxTestRuntime;

@Tag("native")
@Timeout(20)
class ButtonReleaseWaitTest {
    @Test
    void anActionWaitsForTheButtonAndOnlyTheLatestOneRuns() throws Exception {
        var held = new AtomicBoolean(true);
        var ran = new ArrayList<String>();
        var wait = JavaFxTestRuntime.call(() -> new ButtonReleaseWait(held::get));

        JavaFxTestRuntime.call(() -> {
            wait.run(() -> ran.add("first site"));
            wait.run(() -> ran.add("second site"));

            return null;
        });
        Thread.sleep(300);

        assertEquals(List.of(), JavaFxTestRuntime.call(() -> List.copyOf(ran)), "Nothing runs while the button is held");

        held.set(false);
        Thread.sleep(300);

        assertEquals(List.of("second site"), JavaFxTestRuntime.call(() -> List.copyOf(ran)),
                "Once released, the latest action runs");

        JavaFxTestRuntime.call(() -> {
            wait.run(() -> ran.add("third site"));

            return null;
        });

        assertEquals(List.of("second site", "third site"), JavaFxTestRuntime.call(() -> List.copyOf(ran)),
                "Without a held button it runs at once");
    }
}
