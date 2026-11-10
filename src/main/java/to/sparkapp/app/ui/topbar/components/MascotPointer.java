package to.sparkapp.app.ui.topbar.components;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.mouse.NativeMouseEvent;
import com.github.kwhat.jnativehook.mouse.NativeMouseMotionListener;
import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.scene.robot.Robot;
import javafx.util.Duration;
import to.sparkapp.app.utils.FxTimer;

/** Reuses the application's native hook without inspecting the embedded page. */
final class MascotPointer implements NativeMouseMotionListener {
    private final Consumer<Point2D> look;
    private final FxTimer sampleDelay = FxTimer.once(Duration.millis(24), this::sample);
    private final AtomicBoolean queued = new AtomicBoolean();
    private volatile boolean enabled;
    private Robot pointer;

    MascotPointer(Consumer<Point2D> look) {
        this.look = look;
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
                sampleDelay.restart();
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

        look.accept(position());
    }

    /** Where the pointer is now, on the screen. Called on the JavaFX thread. */
    Point2D position() {
        if (pointer == null) {
            pointer = new Robot();
        }

        // Native hook coordinates may be physical pixels on mixed-DPI monitors.
        // JavaFX Robot supplies screen coordinates compatible with screenToLocal.
        return pointer.getMousePosition();
    }

    void dispose() {
        setEnabled(false);
    }
}
