package to.sparkapp.app.ui.topbar.components;

import java.util.function.BooleanSupplier;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Pos;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import to.sparkapp.app.ui.SiteAccent;
import to.sparkapp.app.ui.Theme;

/**
 * A thin line in the site's color along the bottom of the Spark bar while the page loads.
 *
 * <p>It appears only for loads that take a moment, creeps towards the end while the page loads, and
 * completes when it finishes. Every animation ends on its own.
 */
public final class PageLoadingBar extends StackPane {
    public static final double HEIGHT = 2;
    private static final Duration APPEAR_DELAY = Duration.millis(300);
    private static final Duration CREEP = Duration.seconds(8);
    private static final Duration COMPLETE = Duration.millis(180);
    private static final Duration FADE = Duration.millis(250);
    private static final double CREEP_LIMIT = 0.9;
    private static final double STATIC_OPACITY = 0.7;
    private static final Interpolator SLOWING = Interpolator.SPLINE(0.1, 0.6, 0.3, 1);

    private final BooleanSupplier reducedMotion;
    private final Region line = new Region();
    private final DoubleProperty progress = new SimpleDoubleProperty();
    private final PauseTransition appearDelay = new PauseTransition(APPEAR_DELAY);
    private Timeline creep;
    private SequentialTransition finish;
    private boolean loading;

    public PageLoadingBar(BooleanSupplier reducedMotion) {
        this.reducedMotion = reducedMotion;
        setAlignment(Pos.BOTTOM_LEFT);
        setMouseTransparent(true);
        setMinHeight(HEIGHT);
        setPrefHeight(HEIGHT);
        setMaxHeight(HEIGHT);
        line.setMinHeight(HEIGHT);
        line.setMaxHeight(HEIGHT);
        line.maxWidthProperty().bind(widthProperty().multiply(progress));
        line.setOpacity(0);
        getChildren().add(line);
        setAccent(SiteAccent.DEFAULT);
        appearDelay.setOnFinished(event -> appear());
    }

    /** Uses the site's color; dark colors fall back to a light neutral so the line stays visible. */
    public void setAccent(Color accent) {
        var visible = accent.getBrightness() < 0.45 ? SiteAccent.DEFAULT : accent;
        line.setStyle("-fx-background-color:" + Theme.toHex(visible) + ";-fx-background-radius:1;");
    }

    public void setLoading(boolean loading) {
        if (this.loading == loading) {
            return;
        }

        this.loading = loading;

        if (loading) {
            stopAnimations();
            reset();
            appearDelay.playFromStart();
        } else {
            appearDelay.stop();
            complete();
        }
    }

    boolean isShowing() {
        return line.getOpacity() > 0;
    }

    double getProgress() {
        return progress.get();
    }

    private void appear() {
        line.setOpacity(1);

        if (reducedMotion.getAsBoolean()) {
            progress.set(1);
            line.setOpacity(STATIC_OPACITY);

            return;
        }

        progress.set(0);
        creep = new Timeline(new KeyFrame(CREEP, new KeyValue(progress, CREEP_LIMIT, SLOWING)));
        creep.play();
    }

    private void complete() {
        if (!isShowing()) {
            return;
        }

        stopAnimations();

        if (reducedMotion.getAsBoolean()) {
            reset();

            return;
        }

        var fill = new Timeline(new KeyFrame(COMPLETE, new KeyValue(progress, 1, Interpolator.EASE_OUT)));
        var fade = new Timeline(new KeyFrame(FADE, new KeyValue(line.opacityProperty(), 0, Interpolator.EASE_IN)));
        finish = new SequentialTransition(fill, fade);
        finish.setOnFinished(event -> reset());
        finish.play();
    }

    private void stopAnimations() {
        if (creep != null) {
            creep.stop();
        }

        if (finish != null) {
            finish.stop();
        }
    }

    private void reset() {
        line.setOpacity(0);
        progress.set(0);
    }
}
