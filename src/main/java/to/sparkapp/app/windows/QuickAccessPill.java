package to.sparkapp.app.windows;

import java.util.function.Supplier;

import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.geometry.Rectangle2D;
import javafx.scene.robot.Robot;
import javafx.stage.Stage;
import javafx.util.Duration;
import to.sparkapp.app.config.AppPreferences;

final class QuickAccessPill {
    private final Stage stage;
    private final AppPreferences preferences;
    private final TopEdgeZone edge;
    private final Runnable reveal;
    private final QuickAccessPillWindow pill;
    private final FullscreenGuard fullscreen;
    private final PauseTransition edgeHover = new PauseTransition(Duration.millis(300));
    private final PauseTransition pillDismissal = new PauseTransition(Duration.millis(180));
    private boolean disposed;
    private boolean edgeArmed = true;

    QuickAccessPill(Stage stage, AppPreferences preferences, Supplier<Rectangle2D> monitor, Runnable reveal) {
        this(stage, preferences, monitor, reveal, new NativeFullscreenSensor(monitor));
    }

    QuickAccessPill(Stage stage, AppPreferences preferences, Supplier<Rectangle2D> monitor, Runnable reveal,
                    FullscreenSensor fullscreenSensor) {
        this.stage = stage;
        this.preferences = preferences;
        edge = new TopEdgeZone(monitor);
        this.reveal = reveal;
        fullscreen = new FullscreenGuard(preferences, fullscreenSensor);
        pill = new QuickAccessPillWindow(preferences, monitor, reveal, pillDismissal::stop, this::schedulePillHide);
        edgeHover.setOnFinished(event -> revealAfterHover());
    }

    void windowShown() {
        edgeArmed = false;
        edgeHover.stop();
        fullscreen.stopWatching();
        pill.hideImmediately();
    }

    void dispose() {
        disposed = true;
        edgeHover.stop();
        fullscreen.stopWatching();
        pill.close();
    }

    void handleHiddenPointer(double x, double y, PointerApproach approach) {
        if (disposed || stage.isShowing()) {
            edgeHover.stop();
            return;
        }

        var atEdge = preferences.isTopPill() ? edge.nearPill(x, y, false) : edge.nearTopEdge(x, y, false, 200);
        if (!atEdge) {
            edgeArmed = true;
        } else if (fullscreen.blocksTopEdge()) {
            // A game, video or presentation fills this monitor: no preview, hover or gesture.
            edgeHover.stop();
            schedulePillHide();
            return;
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
        if (edge.nearPill(x, y, pill.isShowing())) {
            pillDismissal.stop();
            pill.show(true);
        } else {
            schedulePillHide();
        }
    }

    private void revealAfterHover() {
        var current = new Robot().getMousePosition();
        if (!disposed && !stage.isShowing() && edgeArmed && preferences.isTopPill() && preferences.isTopEdgeReveal() &&
                edge.nearPill(current.getX(), current.getY(), false) && !fullscreen.blocksTopEdge()) {
            edgeArmed = false;
            reveal.run();
        }
    }

    private boolean canRevealQuickly(boolean atEdge, PointerApproach approach) {
        if (!atEdge || !edgeArmed || !preferences.isFastTopEdgeReveal() || approach == null) {
            return false;
        }

        var screen = edge.screen();
        return approach.isFastUpward(screen.getOutputScaleX(), screen.getOutputScaleY(), System.nanoTime());
    }

    void refreshPill() {
        edgeHover.stop();
        if (disposed || stage.isShowing()) {
            fullscreen.stopWatching();
            pill.hideImmediately();
            return;
        }

        var current = new Robot().getMousePosition();
        edgeArmed = edgeArmed || !edge.nearPill(current.getX(), current.getY(), false);
        if (!preferences.isTopPill()) {
            fullscreen.stopWatching();
            pill.hideImmediately();
            return;
        }
        fullscreen.watchPill(this::followFullscreen);
        followFullscreen();
    }

    /** The always-visible pill steps aside while a fullscreen app fills its monitor. */
    private void followFullscreen() {
        if (disposed || stage.isShowing() || !preferences.isTopPill()) {
            return;
        }

        if (fullscreen.hidesPill()) {
            pill.hideImmediately();
        } else {
            pill.show(false);
            fullscreen.pillShown(pill.windowHandle());
        }
    }

    private void schedulePillHide() {
        if (!pill.canDismiss() || pillDismissal.getStatus() == Timeline.Status.RUNNING) {
            return;
        }
        pillDismissal.setOnFinished(event -> {
            var point = new Robot().getMousePosition();
            if (!preferences.isTopPill() && !edge.nearPill(point.getX(), point.getY(), true)) {
                pill.animate(false);
            }
        });
        pillDismissal.playFromStart();
    }
}
