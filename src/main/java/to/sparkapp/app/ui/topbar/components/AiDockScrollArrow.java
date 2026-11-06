package to.sparkapp.app.ui.topbar.components;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/** Scroll controls occupy their own space, outside the tab viewport. */
public final class AiDockScrollArrow extends StackPane {
    private final Timeline hold;
    private final Timeline delay;

    public AiDockScrollArrow(boolean left, AiDock dock) {
        setMinSize(20, 30); setPrefSize(20, 30); setMaxSize(20, 30);
        setAlignment(Pos.CENTER); setCursor(Cursor.HAND);
        getStyleClass().add("dock-scroll-arrow");
        var glyph = new Label(left ? "‹" : "›"); glyph.setMouseTransparent(true);
        getChildren().add(glyph);
        setAccessibleText(left ? "Previous sites" : "More sites");
        Tooltip.install(this, ShellTooltip.create(getAccessibleText()));
        Runnable step = () -> {
            var pane = dock.getScrollPane();
            double overflow = dock.getDockContainer().getWidth() - pane.getViewportBounds().getWidth();
            if (overflow > 0) pane.setHvalue(Math.clamp(pane.getHvalue() + (left ? -36 : 36) / overflow, 0, 1));
        };
        hold = new Timeline(new KeyFrame(Duration.millis(80), e -> step.run())); hold.setCycleCount(Animation.INDEFINITE);
        delay = new Timeline(new KeyFrame(Duration.millis(300), e -> hold.play()));
        setOnMousePressed(e -> { step.run(); if (!isDisabled()) delay.playFromStart(); e.consume(); });
        setOnMouseReleased(e -> { cancel(); e.consume(); });
        setOnMouseExited(e -> cancel());
        disabledProperty().addListener((o, before, disabled) -> { if (disabled) cancel(); });
    }

    public void cancel() { delay.stop(); hold.stop(); }
}
