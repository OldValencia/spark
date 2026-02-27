package to.sparkapp.app.ui.topbar.components;

import to.sparkapp.app.ui.Theme;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.event.Event;
import javafx.scene.Cursor;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.util.Duration;

public class AnimatedIconButton extends StackPane {

    private static final double SIZE   = 30.0;
    private static final double RING_R = 13.0;
    private static final int    ANIM_MS = 130;

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
        ring.setOpacity(0.0);

        this.getChildren().addAll(ring, iconNode);

        var enterTimeline = new Timeline(new KeyFrame(Duration.millis(ANIM_MS),
                new KeyValue(iconNode.fillProperty(), hoverColor),
                new KeyValue(ring.opacityProperty(), 1.0)
        ));

        var exitTimeline = new Timeline(new KeyFrame(Duration.millis(ANIM_MS),
                new KeyValue(iconNode.fillProperty(), Theme.TEXT_SECONDARY),
                new KeyValue(ring.opacityProperty(), 0.0)
        ));

        this.setOnMouseEntered(e -> {
            exitTimeline.stop();
            enterTimeline.play();
        });
        this.setOnMouseExited(e -> {
            enterTimeline.stop();
            exitTimeline.play();
        });

        this.setOnMousePressed(Event::consume);

        this.setOnMouseClicked(e -> {
            e.consume();
            if (action != null) action.run();
        });
    }
}
