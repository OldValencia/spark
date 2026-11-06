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
    private final QuickAccessPillWindow pill;
    private final PauseTransition edgeHover = new PauseTransition(Duration.millis(300));
    private final PauseTransition pillDismissal = new PauseTransition(Duration.millis(180));
    private boolean disposed;
    private boolean edgeArmed = true;

    QuickAccessPill(Stage stage, AppPreferences preferences, Supplier<Rectangle2D> monitor, Runnable reveal) {
        this.stage = stage;
        this.preferences = preferences;
        this.monitor = monitor;
        this.reveal = reveal;
        pill = new QuickAccessPillWindow(preferences, monitor, reveal, pillDismissal::stop, this::schedulePillHide);
        edgeHover.setOnFinished(event -> revealAfterHover());
    }

    void windowShown() {
        edgeArmed = false;
        edgeHover.stop();
        pill.hideImmediately();
    }

    void dispose() {
        disposed = true;
        edgeHover.stop();
        pill.close();
    }

    void handleHiddenPointer(double x, double y, PointerApproach approach) {
        if (disposed || stage.isShowing()) {
            edgeHover.stop();
            return;
        }

        var atEdge = nearPill(x, y, false);
        if (!atEdge) {
            edgeArmed = true;
        }
        if (!preferences.isTopPill()) {
            edgeHover.stop();
            previewOrReveal(x, y, atEdge, approach);
            return;
        }
        if (!preferences.isTopEdgeReveal() || !atEdge || !edgeArmed) {
            edgeHover.stop();
            return;
        }
        if (edgeHover.getStatus() != Timeline.Status.RUNNING) {
            edgeHover.playFromStart();
        }
    }

    private void previewOrReveal(double x, double y, boolean atEdge, PointerApproach approach) {
        if (canRevealQuickly(atEdge, approach)) {
            edgeArmed = false;
            reveal.run();
            return;
        }
        if (nearPill(x, y, pill.isShowing())) {
            pillDismissal.stop();
            pill.show(true);
        } else {
            schedulePillHide();
        }
    }

    private void revealAfterHover() {
        var current = new Robot().getMousePosition();
        if (!disposed && !stage.isShowing() && edgeArmed && preferences.isTopPill() && preferences.isTopEdgeReveal() &&
                nearPill(current.getX(), current.getY(), false)) {
            edgeArmed = false;
            reveal.run();
        }
    }

    private boolean canRevealQuickly(boolean atEdge, PointerApproach approach) {
        if (!atEdge || !edgeArmed || pill.isShowing() || !preferences.isFastTopEdgeReveal() || approach == null) {
            return false;
        }

        var screen = targetScreen();
        return approach.isFastUpward(screen.getOutputScaleX(), screen.getOutputScaleY(), System.nanoTime());
    }

    private Screen targetScreen() {
        return Screen.getScreens().stream().filter(
                screen -> screen.getVisualBounds().equals(monitor.get())).findFirst().orElse(Screen.getPrimary());
    }

    private boolean nearPill(double x, double y, boolean includePill) {
        var bounds = targetScreen().getBounds();
        var center = monitor.get().getMinX() + monitor.get().getWidth() / 2;
        var bottom = includePill ? monitor.get().getMinY() + 36 : bounds.getMinY() + 10;
        return Math.abs(x - center) <= 110 && y >= bounds.getMinY() && y <= bottom;
    }

    void refreshPill() {
        edgeHover.stop();
        if (disposed || stage.isShowing()) {
            pill.hideImmediately();
            return;
        }

        var current = new Robot().getMousePosition();
        edgeArmed = edgeArmed || !nearPill(current.getX(), current.getY(), false);
        if (!preferences.isTopPill()) {
            pill.hideImmediately();
            return;
        }
        pill.show(false);
    }

    private void schedulePillHide() {
        if (!pill.canDismiss() || pillDismissal.getStatus() == Timeline.Status.RUNNING) {
            return;
        }
        pillDismissal.setOnFinished(event -> {
            var point = new Robot().getMousePosition();
            if (!preferences.isTopPill() && !nearPill(point.getX(), point.getY(), true)) {
                pill.animate(false);
            }
        });
        pillDismissal.playFromStart();
    }
}
