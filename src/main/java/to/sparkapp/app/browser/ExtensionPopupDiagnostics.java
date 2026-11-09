package to.sparkapp.app.browser;

import java.util.Arrays;
import java.util.UUID;

import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

/** Records geometry only; extension text, input values, URLs, and vault contents are excluded. */
@Slf4j
final class ExtensionPopupDiagnostics {
    private final String session = UUID.randomUUID().toString().substring(0, 8);
    private final long started = System.nanoTime();
    private String previousMeasurement;
    private long lastMeasurementLog;

    void opened(String name, String id, Stage window, long handle) {
        log.info("Extension popup [{}] opened: name={}, id={}, host={}x{}, scale={}x{}, hwnd={}", session,
                name, id, window.getWidth(), window.getHeight(), window.getOutputScaleX(), window.getOutputScaleY(), handle);
    }

    void measurement(String json) {
        var now = System.nanoTime();
        if (!json.equals(previousMeasurement) && (lastMeasurementLog == 0 || now - lastMeasurementLog > 1_000_000_000L)) {
            previousMeasurement = json;
            lastMeasurementLog = now;
            log.debug("Extension popup [{}] measurement: {}", session, json);
        }
    }

    void viewport(int[] parent, int[] previous, int width, int height, int inset) {
        log.debug("Extension popup [{}] native viewport: parent={}, previous={}, target={}x{}, inset={}",
                session, Arrays.toString(parent), Arrays.toString(previous), width, height, inset);
    }

    void resized(ExtensionPopupSize size, double width, double height) {
        log.debug("Extension popup [{}] resize: document={}x{}, ratio={}, targetHost={}x{}", session,
                size.width(), size.height(), size.ratio(), width, height);
    }

    void failed(Throwable error) {
        log.warn("Extension popup [{}] failed", session, error);
    }

    void closed(Stage window) {
        log.info("Extension popup [{}] closed: host={}x{}, elapsedMs={}", session, window.getWidth(),
                window.getHeight(), (System.nanoTime() - started) / 1_000_000);
    }
}
