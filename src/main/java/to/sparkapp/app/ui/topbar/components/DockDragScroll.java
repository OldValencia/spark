package to.sparkapp.app.ui.topbar.components;

import javafx.animation.PauseTransition;
import javafx.scene.input.DragEvent;
import javafx.util.Duration;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Scrolls the tabs while something dragged from outside, such as a file, nears an edge of the tab strip or rests on a
 * scroll arrow, so every tab can be reached during the drag.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class DockDragScroll {
    /** Stops scrolling if the drag goes quiet, in case it ended without saying so, for example after Esc. */
    private static final Duration SILENCE_LIMIT = Duration.seconds(2);

    static void install(AiDock dock) {
        var scrolling = dock.getDragManager();
        var silence = new PauseTransition(SILENCE_LIMIT);
        silence.setOnFinished(event -> scrolling.stopAutoscroll());

        dock.addEventHandler(DragEvent.DRAG_OVER, event -> {
            scrolling.startAutoscrollIfNeeded(event.getSceneX());
            silence.playFromStart();
        });
        dock.addEventHandler(DragEvent.DRAG_EXITED, event -> {
            silence.stop();
            scrolling.stopAutoscroll();
        });
    }
}
