package to.sparkapp.app.ui.webview;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import lombok.Getter;
import lombok.Setter;
import to.sparkapp.app.browser.WebviewManager;
import to.sparkapp.app.utils.NativeWindowUtils;
import to.sparkapp.app.utils.SystemUtils;

final class BrowserViewport {
    private final StackPane host;
    private final Supplier<WebviewManager> browser;
    private final BooleanSupplier started;
    private final Timeline hostGeometryWatchdog = new Timeline(
            new KeyFrame(Duration.millis(400), event -> checkHostGeometry()));
    @Getter
    @Setter
    private long parentHandle;
    private int lastHostClientWidth = -1;
    private int lastHostClientHeight = -1;
    private int lastHostDpi = -1;

    BrowserViewport(StackPane host, Supplier<WebviewManager> browser, BooleanSupplier started) {
        this.host = host;
        this.browser = browser;
        this.started = started;
        hostGeometryWatchdog.setCycleCount(Animation.INDEFINITE);
    }

    void stop() {
        hostGeometryWatchdog.stop();
    }

    int[] currentBounds() {
        return BrowserBounds.calculate(host, parentHandle);
    }

    void syncBounds() {
        if (!started.getAsBoolean() || host.getScene() == null || host.getScene().getWindow() == null) {
            return;
        }

        var bounds = currentBounds();

        if (bounds[2] > 0 && bounds[3] > 0) {
            browser.get().updateBounds(bounds[0], bounds[1], bounds[2], bounds[3]);
        }
    }

    void start() {
        if (SystemUtils.isWindows() && hostGeometryWatchdog.getStatus() != Animation.Status.RUNNING) {
            hostGeometryWatchdog.play();
        }
    }

    void resync() {
        syncBounds();
        Platform.runLater(this::syncBounds);

        for (int delayMs : new int[]{80, 250, 600}) {
            var delay = new PauseTransition(Duration.millis(delayMs));
            delay.setOnFinished(e -> syncBounds());
            delay.play();
        }
    }

    private void checkHostGeometry() {
        if (!started.getAsBoolean() || parentHandle == 0) {
            return;
        }

        var window = host.getScene() != null ? host.getScene().getWindow() : null;

        if (window == null || !window.isShowing()) {
            return;
        }

        int[] client = NativeWindowUtils.getClientSize(parentHandle);

        if (client == null) {
            return;
        }

        int dpi = NativeWindowUtils.getWindowDpi(parentHandle);

        if (client[0] != lastHostClientWidth || client[1] != lastHostClientHeight || dpi != lastHostDpi) {
            lastHostClientWidth = client[0];
            lastHostClientHeight = client[1];
            lastHostDpi = dpi;

            host.applyCss();
            host.layout();
            syncBounds();
        }
    }
}
