package to.sparkapp.app.ui.topbar.components;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.mouse.NativeMouseEvent;
import com.github.kwhat.jnativehook.mouse.NativeMouseMotionListener;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.scene.robot.Robot;
import javafx.util.Duration;

/** Reuses the application's native hook without inspecting the embedded page. */
final class MascotPointer implements NativeMouseMotionListener {
    private final Consumer<Point2D> look;
    private final PauseTransition sampleDelay = new PauseTransition(Duration.millis(24));
    private final AtomicBoolean queued = new AtomicBoolean();
    private volatile boolean enabled;
    private Robot pointer;

    MascotPointer(Consumer<Point2D> look) {
        this.look = look;
        sampleDelay.setOnFinished(event -> sample());
    }

    void setEnabled(boolean requested) {
        if (enabled == requested) {
            return;
        }

        enabled = requested;

        if (requested) {
            GlobalScreen.addNativeMouseMotionListener(this);
        } else {
            GlobalScreen.removeNativeMouseMotionListener(this);
            sampleDelay.stop();
            queued.set(false);
        }
    }

    @Override
    public void nativeMouseMoved(NativeMouseEvent event) {
        if (!enabled || !queued.compareAndSet(false, true)) {
            return;
        }

        Platform.runLater(() -> {
            if (enabled) {
                sampleDelay.playFromStart();
            } else {
                queued.set(false);
            }
        });
    }

    @Override
    public void nativeMouseDragged(NativeMouseEvent event) {
        nativeMouseMoved(event);
    }

    private void sample() {
        queued.set(false);

        if (!enabled) {
            return;
        }

        if (pointer == null) {
            pointer = new Robot();
        }

        // Native hook coordinates may be physical pixels on mixed-DPI monitors.
        // JavaFX Robot supplies screen coordinates compatible with screenToLocal.
        look.accept(pointer.getMousePosition());
    }

    void dispose() {
        setEnabled(false);
    }
}
