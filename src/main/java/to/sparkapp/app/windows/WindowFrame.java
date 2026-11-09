package to.sparkapp.app.windows;

import java.util.function.BooleanSupplier;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import to.sparkapp.app.ui.Theme;

/**
 * Draws the window shell: a rounded panel whose outline takes a hint of the open site's color.
 */
final class WindowFrame {
    static final Color BACKGROUND = Color.web("#25262a");
    static final Color NEUTRAL_OUTLINE = Color.web("#414247");
    private static final double ACCENT_SHARE = 0.3;
    private static final Duration TRANSITION = Duration.millis(200);

    private final Region shell;
    private final BooleanSupplier reducedMotion;
    private final ObjectProperty<Color> outline = new SimpleObjectProperty<>(NEUTRAL_OUTLINE);
    private Timeline transition;
    private boolean docked;

    WindowFrame(Region shell, BooleanSupplier reducedMotion) {
        this.shell = shell;
        this.reducedMotion = reducedMotion;
        outline.addListener((observable, previous, current) -> render());
        render();
    }

    void setDocked(boolean docked) {
        this.docked = docked;
        render();
    }

    /** Tints the outline for the selected site, fading while the window is on screen. */
    void showAccent(Color accent) {
        var target = outlineFor(accent);

        if (transition != null) {
            transition.stop();
        }

        if (target.equals(outline.get()) || !animated()) {
            outline.set(target);

            return;
        }

        transition = new Timeline(new KeyFrame(TRANSITION, new KeyValue(outline, target, Interpolator.EASE_BOTH)));
        transition.play();
    }

    Color getOutline() {
        return outline.get();
    }

    /** Dark site colors would hide the edge of the window, so they keep the neutral outline. */
    static Color outlineFor(Color accent) {
        if (accent.getBrightness() <= NEUTRAL_OUTLINE.getBrightness()) {
            return NEUTRAL_OUTLINE;
        }

        return Theme.lerp(NEUTRAL_OUTLINE, accent, ACCENT_SHARE);
    }

    private boolean animated() {
        var scene = shell.getScene();
        var showing = scene != null && scene.getWindow() != null && scene.getWindow().isShowing();

        return showing && !reducedMotion.getAsBoolean();
    }

    private void render() {
        var radius = docked ? "0 0 16 16" : "16";
        shell.setStyle("-fx-background-color:" + Theme.toHex(BACKGROUND) + ";-fx-background-radius:" + radius +
                ";-fx-border-radius:" + radius + ";-fx-border-color:" + Theme.toHex(outline.get()) +
                ";-fx-border-width:1;");
    }
}
