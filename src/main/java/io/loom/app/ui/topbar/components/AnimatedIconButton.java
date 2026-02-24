package io.loom.app.ui.topbar.components;

import io.loom.app.ui.Theme;
import javafx.event.Event;
import javafx.scene.Cursor;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;

public class AnimatedIconButton extends StackPane {

    private static final double SIZE = 30.0;
    private static final double RING_R = 13.0;

    public AnimatedIconButton(String icon, Color hoverColor, Runnable action) {
        this.setPrefSize(SIZE, SIZE);
        this.setMinSize(SIZE, SIZE);
        this.setMaxSize(SIZE, SIZE);
        this.setCursor(Cursor.HAND);

        var iconNode = new Text(icon);
        iconNode.setFont(Theme.FONT_RIGHT_TOP_BAR_AREA);
        iconNode.setFill(Theme.TEXT_SECONDARY);

        var ring = new Circle(RING_R);
        ring.setFill(Color.TRANSPARENT);
        ring.setStroke(Theme.withAlpha(Theme.BTN_RING, 0.6));
        ring.setStrokeWidth(1.3);
        ring.setOpacity(0);

        this.getChildren().addAll(ring, iconNode);

        this.setOnMouseEntered(e -> {
            iconNode.setFill(hoverColor);
            ring.setOpacity(1.0);
        });

        this.setOnMouseExited(e -> {
            iconNode.setFill(Theme.TEXT_SECONDARY);
            ring.setOpacity(0.0);
        });

        this.setOnMousePressed(Event::consume);

        this.setOnMouseClicked(e -> {
            e.consume();
            if (action != null) {
                action.run();
            }
        });
    }
}
