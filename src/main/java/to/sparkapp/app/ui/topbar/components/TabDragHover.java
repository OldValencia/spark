package to.sparkapp.app.ui.topbar.components;

import javafx.animation.PauseTransition;
import javafx.scene.Node;
import javafx.scene.input.DragEvent;
import javafx.util.Duration;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Opens a tab's site when something dragged, such as a file from Explorer, rests on the tab, so it can then be dropped
 * into that site's page, as in a browser. The pause keeps tabs from switching while a file is only carried past them.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TabDragHover {
    static final Duration DELAY = Duration.millis(500);

    static void install(Node tab, Runnable open) {
        var wait = new PauseTransition(DELAY);
        wait.setOnFinished(event -> open.run());
        tab.addEventHandler(DragEvent.DRAG_ENTERED, event -> wait.playFromStart());
        tab.addEventHandler(DragEvent.DRAG_EXITED, event -> wait.stop());
        tab.addEventHandler(DragEvent.DRAG_DROPPED, event -> wait.stop());
    }
}
