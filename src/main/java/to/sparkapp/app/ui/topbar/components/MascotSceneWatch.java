package to.sparkapp.app.ui.topbar.components;

import java.util.function.Consumer;

import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.input.DragEvent;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;

/**
 * Watches Spark's window for signs of the user: the pointer, typing, and a file carried over the window. Nothing is
 * consumed, so the window handles every event as before.
 */
final class MascotSceneWatch {
    /** A file carried in again after this pause counts as a new arrival. */
    private static final long NEW_ARRIVAL_NANOS = 1_000_000_000L;

    private final EventHandler<MouseEvent> pointer;
    private final EventHandler<KeyEvent> typing;
    private final EventHandler<DragEvent> carrying;
    private long lastCarried = Long.MIN_VALUE;

    /**
     * @param pointerAt   receives the pointer's position in the scene
     * @param typed       runs on a key press in Spark
     * @param fileAt      receives the position of a file carried over the scene
     * @param fileArrived runs when a file is first carried over the scene
     */
    MascotSceneWatch(Consumer<Point2D> pointerAt, Runnable typed, Consumer<Point2D> fileAt, Runnable fileArrived) {
        pointer = event -> pointerAt.accept(new Point2D(event.getSceneX(), event.getSceneY()));
        typing = event -> typed.run();
        carrying = event -> {
            if (!event.getDragboard().hasFiles()) {
                return;
            }

            var now = System.nanoTime();

            if (lastCarried == Long.MIN_VALUE || now - lastCarried >= NEW_ARRIVAL_NANOS) {
                fileArrived.run();
            }

            lastCarried = now;
            fileAt.accept(new Point2D(event.getSceneX(), event.getSceneY()));
        };
    }

    void move(Scene before, Scene scene) {
        if (before != null) {
            before.removeEventFilter(MouseEvent.MOUSE_MOVED, pointer);
            before.removeEventFilter(KeyEvent.KEY_PRESSED, typing);
            before.removeEventFilter(DragEvent.DRAG_OVER, carrying);
        }

        if (scene != null) {
            scene.addEventFilter(MouseEvent.MOUSE_MOVED, pointer);
            scene.addEventFilter(KeyEvent.KEY_PRESSED, typing);
            scene.addEventFilter(DragEvent.DRAG_OVER, carrying);
        }
    }
}
