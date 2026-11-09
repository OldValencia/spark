package to.sparkapp.app.windows;

import java.util.concurrent.atomic.AtomicInteger;

import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import to.sparkapp.app.config.AppPreferences;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class QuickAccessPillFixture implements AutoCloseable {
    final AppPreferences preferences = new AppPreferences();
    final Stage stage = new Stage();
    final Screen screen = Screen.getPrimary();
    final AtomicInteger opens = new AtomicInteger();
    final FakeFullscreenSensor fullscreen = new FakeFullscreenSensor();
    final QuickAccessPill pill;
    private final boolean previousPill = preferences.isTopPill();
    private final boolean previousQuickOpen = preferences.isFastTopEdgeReveal();
    private final boolean previousQuietInFullscreen = preferences.isTopEdgeQuietInFullscreen();

    QuickAccessPillFixture(boolean autoHide, boolean quickOpen) {
        assertEquals("true", System.getProperty("spark.nativeTests"));
        preferences.setTopPill(!autoHide);
        preferences.setFastTopEdgeReveal(quickOpen);
        preferences.setTopEdgeQuietInFullscreen(true);
        pill = new QuickAccessPill(stage, preferences, screen::getVisualBounds, opens::incrementAndGet,
                fullscreen);
    }

    double centerX() {
        var bounds = screen.getVisualBounds();
        return bounds.getMinX() + bounds.getWidth() / 2;
    }

    double edgeY() {
        return screen.getBounds().getMinY() + 1;
    }

    PointerApproach approach(double upwardDistance, long durationNanos) {
        return new PointerApproach(0, upwardDistance * screen.getOutputScaleY(), durationNanos, System.nanoTime());
    }

    int visiblePills() {
        return (int) Window.getWindows().stream().filter(
                window -> window instanceof Stage visible && visible.isShowing() &&
                        visible.getTitle().startsWith("Spark quick access ")).count();
    }

    @Override
    public void close() {
        pill.dispose();
        stage.close();
        preferences.setTopPill(previousPill);
        preferences.setFastTopEdgeReveal(previousQuickOpen);
        preferences.setTopEdgeQuietInFullscreen(previousQuietInFullscreen);
        preferences.flush();
    }
}
