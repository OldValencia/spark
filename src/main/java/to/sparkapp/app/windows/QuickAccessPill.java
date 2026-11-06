package to.sparkapp.app.windows;

import java.util.function.Supplier;

import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.geometry.Rectangle2D;
import javafx.scene.robot.Robot;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;
import to.sparkapp.app.config.AppPreferences;

final class QuickAccessPill {
    private final Stage stage;
    private final AppPreferences preferences;
    private final Supplier<Rectangle2D> monitor;
    private final Runnable reveal;
    private boolean disposed;
    private final QuickAccessPillWindow pill;
    private final PauseTransition edgeHover = new PauseTransition(Duration.millis(300));
    private final PauseTransition pillDismissal = new PauseTransition(Duration.millis(180));

    QuickAccessPill(Stage stage, AppPreferences preferences, Supplier<Rectangle2D> monitor, Runnable reveal) {
        this.stage = stage;
        this.preferences = preferences;
        this.monitor = monitor;
        this.reveal = reveal;
        pill = new QuickAccessPillWindow(preferences, monitor, reveal, pillDismissal::stop, this::schedulePillHide);
    }

    void windowShown() {
        edgeHover.stop();
        pill.hideImmediately();
    }

    void dispose() {
        disposed = true;
        edgeHover.stop();
        pill.hideImmediately();
        pill.close();
    }

    public void handleHiddenPointer(double x, double y) {
        if (disposed || stage.isShowing()) {
            edgeHover.stop();

            return;
        }

        if (!preferences.isTopPill()) {
            edgeHover.stop();

            if (nearPill(x, y, pill.isShowing())) {
                pillDismissal.stop();
                pill.show(true);
            } else {
                schedulePillHide();
            }

            return;
        }

        if (!preferences.isTopEdgeReveal()) {
            edgeHover.stop();

            return;
        }

        if (!nearPill(x, y, false)) {
            edgeHover.stop();

            return;
        }

        if (edgeHover.getStatus() != Timeline.Status.RUNNING) {
            edgeHover.setOnFinished(e -> {
                var current = new Robot().getMousePosition();

                if (!disposed && !stage.isShowing() && preferences.isTopPill() && preferences.isTopEdgeReveal() &&
                        nearPill(current.getX(), current.getY(), false)) {
                    reveal.run();
                }
            });
            edgeHover.playFromStart();
        }
    }

    private boolean nearPill(double x, double y, boolean includePill) {
        var screen = Screen.getScreens().stream().filter(s -> s.getVisualBounds().equals(monitor.get())).findFirst()
                .orElse(Screen.getPrimary());
        var bounds = screen.getBounds();
        double center = monitor.get().getMinX() + monitor.get().getWidth() / 2;
        double bottom = includePill ? monitor.get().getMinY() + 36 : bounds.getMinY() + 10;

        return Math.abs(x - center) <= 110 && y >= bounds.getMinY() && y <= bottom;
    }

    public void refreshPill() {
        if (disposed || stage.isShowing() || !preferences.isTopPill()) {
            pill.hideImmediately();

            return;
        }

        pill.show(false);
    }

    private void schedulePillHide() {
        if (!pill.canDismiss() || pillDismissal.getStatus() == Timeline.Status.RUNNING) {
            return;
        }

        pillDismissal.setOnFinished(e -> {
            var point = new Robot().getMousePosition();

            if (!preferences.isTopPill() && !nearPill(point.getX(), point.getY(), true)) {
                pill.animate(false);
            }
        });
        pillDismissal.playFromStart();
    }

}
