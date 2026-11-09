package to.sparkapp.app.ui.topbar.components;

import java.util.function.BooleanSupplier;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.InvalidationListener;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import to.sparkapp.app.ui.SiteAccent;
import to.sparkapp.app.ui.Theme;

/**
 * The highlight behind the selected site tab. It slides to a newly selected tab and then follows
 * it, also while the tab is dragged.
 */
final class TabSelectionIndicator extends Pane {
    private static final Duration SLIDE = Duration.millis(220);
    private static final Interpolator EASE = Interpolator.SPLINE(0.2, 0, 0.2, 1);
    private static final Color BAR = Color.web("#25262a");
    private static final double WASH_SHARE = 0.13;
    private static final double BORDER_SHARE = 0.30;

    private final BooleanSupplier reducedMotion;
    private final Region highlight = new Region();
    private final DoubleProperty progress = new SimpleDoubleProperty(1);
    private final InvalidationListener follow = observable -> update();
    private DockItemNode target;
    private Bounds from = new BoundingBox(0, 0, 0, 0);
    private Color fromAccent = SiteAccent.DEFAULT;
    private Timeline slide;

    TabSelectionIndicator(BooleanSupplier reducedMotion) {
        this.reducedMotion = reducedMotion;
        setMouseTransparent(true);
        setManaged(false);
        highlight.getStyleClass().add("site-tab-highlight");
        highlight.setManaged(false);
        highlight.setVisible(false);
        getChildren().add(highlight);
        progress.addListener(follow);
    }

    /** Moves the highlight to a tab, sliding from the previous one when there was one. */
    void select(DockItemNode tab) {
        if (slide != null) {
            slide.stop();
        }

        var previous = target;
        from = previous == null ? null : currentBounds();
        fromAccent = previous == null ? accentOf(tab) : currentAccent();

        if (previous != null) {
            previous.boundsInParentProperty().removeListener(follow);
        }

        target = tab;

        if (tab == null) {
            highlight.setVisible(false);

            return;
        }

        tab.boundsInParentProperty().addListener(follow);
        highlight.setVisible(true);

        if (previous == null || reducedMotion.getAsBoolean()) {
            progress.set(1);
            update();

            return;
        }

        progress.set(0);
        slide = new Timeline(new KeyFrame(SLIDE, new KeyValue(progress, 1, EASE)));
        slide.play();
    }

    void dispose() {
        select(null);
    }

    Bounds highlightBounds() {
        return highlight.isVisible() ? currentBounds() : null;
    }

    private void update() {
        if (target == null) {
            return;
        }

        var bounds = target.getBoundsInParent();
        var start = from == null ? bounds : from;
        var share = progress.get();
        var x = start.getMinX() + (bounds.getMinX() - start.getMinX()) * share;
        var width = start.getWidth() + (bounds.getWidth() - start.getWidth()) * share;
        highlight.resizeRelocate(x, bounds.getMinY(), width, bounds.getHeight());
        paint(Theme.lerp(fromAccent, accentOf(target), share));
    }

    private Bounds currentBounds() {
        return new BoundingBox(highlight.getLayoutX(), highlight.getLayoutY(), highlight.getWidth(),
                highlight.getHeight());
    }

    private Color currentAccent() {
        return Theme.lerp(fromAccent, accentOf(target), progress.get());
    }

    private void paint(Color accent) {
        var wash = Theme.toHex(Theme.lerp(BAR, accent, WASH_SHARE));
        var border = Theme.toHex(Theme.lerp(BAR, accent, BORDER_SHARE));
        highlight.setStyle("-fx-background-color:" + wash + ";-fx-border-color:" + border + ";");
    }

    private static Color accentOf(DockItemNode tab) {
        return SiteAccent.resolve(tab.getConfig().color());
    }
}
