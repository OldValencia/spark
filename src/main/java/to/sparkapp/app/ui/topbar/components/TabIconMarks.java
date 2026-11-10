package to.sparkapp.app.ui.topbar.components;

import java.util.function.BooleanSupplier;

import javafx.animation.Interpolator;
import javafx.animation.ScaleTransition;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

/**
 * A tab's icon with its marks: a small dot when the site changed while another site was open, and a speaker while the
 * site plays sound. Both sit on the icon, so they never change the tab's width.
 */
final class TabIconMarks extends StackPane {
    private static final Duration POP = Duration.millis(180);

    private final BooleanSupplier reducedMotion;
    private final Node icon;
    private final Circle dot = new Circle(3.2, Color.web("#dfb584"));
    private final ScaleTransition pop = new ScaleTransition(POP, dot);
    private final TabSoundBadge sound = new TabSoundBadge();

    TabIconMarks(Node icon, BooleanSupplier reducedMotion) {
        this.reducedMotion = reducedMotion;
        this.icon = icon;
        dot.setStroke(Color.web("#25262a"));
        dot.setStrokeWidth(1.5);
        dot.setManaged(false);
        dot.setVisible(false);
        dot.setMouseTransparent(true);
        pop.setFromX(0);
        pop.setFromY(0);
        pop.setToX(1);
        pop.setToY(1);
        pop.setInterpolator(Interpolator.EASE_OUT);
        setAlignment(Pos.CENTER);
        getChildren().addAll(icon, dot, sound);
    }

    TabSoundBadge sound() {
        return sound;
    }

    void show() {
        if (dot.isVisible()) {
            return;
        }

        dot.setVisible(true);

        if (reducedMotion.getAsBoolean()) {
            dot.setScaleX(1);
            dot.setScaleY(1);
        } else {
            pop.playFromStart();
        }
    }

    void hide() {
        pop.stop();
        dot.setVisible(false);
    }

    boolean isShown() {
        return dot.isVisible();
    }

    /** The dot sits on the icon's top-right corner, the speaker on its bottom-right corner. */
    @Override
    protected void layoutChildren() {
        super.layoutChildren();

        var bounds = icon.getBoundsInParent();
        dot.setLayoutX(bounds.getMaxX() - 1);
        dot.setLayoutY(bounds.getMinY() + 1);
        sound.relocate(bounds.getMaxX() - 1 - sound.getWidth() / 2, bounds.getMaxY() - 1 - sound.getHeight() / 2);
    }
}
