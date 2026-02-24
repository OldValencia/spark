package io.loom.app.ui.topbar.components;

import io.loom.app.ui.Theme;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.event.Event;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.util.Duration;

public class ZoomButton extends VBox {

    private final Text textNode;
    private final Timeline timeline;

    public ZoomButton(Runnable action) {
        this.textNode = new Text("");
        this.textNode.setFont(Theme.FONT_SELECTOR);
        this.textNode.setFill(Theme.TEXT_SECONDARY);

        var underline = new Rectangle();
        underline.setHeight(1);
        underline.setFill(Theme.TEXT_SECONDARY);
        underline.widthProperty().bind(Bindings.createDoubleBinding(
                () -> textNode.getBoundsInLocal().getWidth(),
                textNode.boundsInLocalProperty()
        ));
        underline.setScaleX(0);

        this.setAlignment(Pos.CENTER);
        this.setSpacing(2);
        this.getChildren().addAll(textNode, underline);

        this.setCursor(Cursor.HAND);
        this.setVisible(false);
        this.setManaged(false);

        this.timeline = new Timeline(
                new KeyFrame(Duration.millis(150),
                        new KeyValue(textNode.fillProperty(), Theme.TEXT_PRIMARY),
                        new KeyValue(underline.scaleXProperty(), 1.0),
                        new KeyValue(underline.fillProperty(), Theme.TEXT_PRIMARY)
                )
        );
        this.setOnMouseClicked(e -> action.run());
        this.setOnMouseEntered(e -> {
            timeline.setRate(1.0);
            timeline.play();
        });
        this.setOnMousePressed(Event::consume);
        this.setOnMouseExited(e -> {
            timeline.setRate(-1.0);
            timeline.play();
        });
    }

    public void updateZoomDisplay(Double percent) {
        Platform.runLater(() -> {
            long val = Math.round(percent);
            if (val == 100) {
                this.setVisible(false);
                this.setManaged(false);
            } else {
                this.textNode.setText(val + "%");
                this.setVisible(true);
                this.setManaged(true);
            }
        });
    }
}
