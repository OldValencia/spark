package to.sparkapp.app.windows;

import java.util.function.Supplier;

import javafx.animation.PauseTransition;
import javafx.geometry.Rectangle2D;
import javafx.util.Duration;
import to.sparkapp.app.utils.ForegroundWatch;
import to.sparkapp.app.utils.FullscreenDetector;

/**
 * Fullscreen checks backed by the foreground window. Bursts of window events, such as a window being dragged,
 * settle into a single check. One more check follows a second later: an app can finish entering fullscreen
 * after its last window event, as Edge sometimes does after F11.
 */
final class NativeFullscreenSensor implements FullscreenSensor {
    private final Supplier<Rectangle2D> monitor;
    private final PauseTransition settle = new PauseTransition(Duration.millis(150));
    private final PauseTransition confirm = new PauseTransition(Duration.seconds(1));
    private final ForegroundWatch watch = new ForegroundWatch(this::windowsChanged);
    private Rectangle2D pillBounds;
    private long pillMonitor;

    NativeFullscreenSensor(Supplier<Rectangle2D> monitor) {
        this.monitor = monitor;
    }

    @Override
    public boolean isUnderPointer() {
        return FullscreenDetector.isFullscreenUnderPointer();
    }

    /** False while the pill's monitor is unknown, for example after Spark moved to another monitor. */
    @Override
    public boolean isOnPillMonitor() {
        return monitor.get().equals(pillBounds) && FullscreenDetector.isFullscreenOnMonitor(pillMonitor);
    }

    @Override
    public void pillShown(long windowHandle) {
        pillMonitor = FullscreenDetector.monitorOf(windowHandle);
        pillBounds = monitor.get();
    }

    @Override
    public void watch(Runnable changed) {
        settle.setOnFinished(event -> changed.run());
        confirm.setOnFinished(event -> changed.run());
        watch.start();
    }

    @Override
    public void stopWatching() {
        watch.stop();
        settle.stop();
        confirm.stop();
    }

    private void windowsChanged() {
        settle.playFromStart();
        confirm.playFromStart();
    }
}
