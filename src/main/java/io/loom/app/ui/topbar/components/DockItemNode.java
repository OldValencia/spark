package io.loom.app.ui.topbar.components;

import io.loom.app.config.AiConfiguration;
import io.loom.app.ui.Theme;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.util.Duration;
import lombok.Getter;

public class DockItemNode extends HBox {

    @Getter
    private final AiConfiguration.AiConfig config;
    private final AiDock parentDock;
    private final Label textLabel;
    private final Timeline timeline;

    private boolean isSelected = false;
    private boolean isDockHovered = false;
    private boolean isItemHovered = false;

    private final double expandedWidth;
    private final double iconWidth;

    public DockItemNode(AiConfiguration.AiConfig config, AiDock parentDock) {
        this.config = config;
        this.parentDock = parentDock;

        this.setHeight(AiDock.ITEM_HEIGHT);
        this.setAlignment(Pos.CENTER_LEFT);
        this.setSpacing(AiDock.GAP);
        this.setCursor(Cursor.HAND);

        this.setBackground(createBackground(Theme.BG_POPUP));
        this.setStyle("""
                    -fx-border-color: %s;
                    -fx-border-radius: %s px;
                    -fx-background-radius: %s px;
                """.formatted(Theme.toHex(Theme.BORDER), AiDock.ITEM_HEIGHT, AiDock.ITEM_HEIGHT));

        var image = AiDock.ICON_CACHE.get(config.icon());
        if (image != null && image.getWidth() > 1) {
            var imageView = new ImageView(image);
            imageView.setFitWidth(AiDock.ICON_SIZE);
            imageView.setFitHeight(AiDock.ICON_SIZE);
            this.getChildren().add(imageView);
        } else {
            var circle = new Circle(AiDock.ICON_SIZE / 2.0, Color.GRAY);
            this.getChildren().add(circle);
        }

        textLabel = new Label(config.name());
        textLabel.setFont(Theme.FONT_SELECTOR);
        textLabel.setTextFill(Theme.TEXT_PRIMARY);
        textLabel.setOpacity(0.0);
        this.getChildren().add(textLabel);

        var tempText = new Text(config.name());
        tempText.setFont(Theme.FONT_SELECTOR);
        var textWidth = tempText.getLayoutBounds().getWidth();

        this.expandedWidth = AiDock.PAD + AiDock.ICON_SIZE + AiDock.GAP + textWidth + AiDock.PAD;
        this.iconWidth = AiDock.PAD + AiDock.ICON_SIZE + AiDock.PAD;

        this.setPrefWidth(0);
        this.setMaxWidth(0);

        var clipRect = new Rectangle();
        clipRect.widthProperty().bind(this.widthProperty());
        clipRect.heightProperty().bind(this.heightProperty());
        this.setClip(clipRect);

        timeline = new Timeline();

        setupInteraction();
        updateState();
    }

    private void setupInteraction() {
        this.setOnMouseEntered(e -> {
            isItemHovered = true;
            updateState();
            if (!isSelected) {
                this.setBackground(createBackground(Theme.BG_HOVER));
            }
        });

        this.setOnMouseExited(e -> {
            isItemHovered = false;
            updateState();
            if (!isSelected) {
                this.setBackground(createBackground(Theme.BG_POPUP));
            }
        });

        this.setOnMouseClicked(e -> parentDock.selectItem(this));

        this.setOnMouseDragged(e -> {
            isItemHovered = true;
            parentDock.handleDrag(this, e.getSceneX());
        });
    }

    public void setDockHovered(boolean dockHovered) {
        this.isDockHovered = dockHovered;
        updateState();
    }

    public void setSelected(boolean selected) {
        this.isSelected = selected;
        if (selected) {
            var accent = Theme.ACCENT;
            try {
                if (config.color() != null) {
                    accent = Color.web(config.color());
                }
            } catch (Exception ignored) {
            }
            this.setBackground(createBackground(accent));
            this.setStyle("-fx-border-color: transparent; -fx-background-radius: " + AiDock.ITEM_HEIGHT + "px;");
        } else {
            this.setBackground(createBackground(Theme.BG_POPUP));
            this.setStyle("""
                        -fx-border-color: %s;
                        -fx-border-radius: %dpx;
                        -fx-background-radius: %dpx;
                    """.formatted(Theme.toHex(Theme.BORDER), AiDock.ITEM_HEIGHT, AiDock.ITEM_HEIGHT));
        }
        updateState();
    }

    private void updateState() {
        timeline.stop();
        timeline.getKeyFrames().clear();

        var targetWidth = 0.0;
        var textOpacity = 0.0;

        if (isSelected) {
            targetWidth = expandedWidth;
            textOpacity = 1.0;
        } else {
            if (isDockHovered || isItemHovered) {
                if (isItemHovered) {
                    targetWidth = expandedWidth;
                    textOpacity = 1.0;
                } else {
                    targetWidth = iconWidth;
                    textOpacity = 0.0;
                }
            } else {
                targetWidth = 0.0;
                textOpacity = 0.0;
            }
        }

        var paddingLeft = (targetWidth == iconWidth) ? (iconWidth - AiDock.ICON_SIZE) / 2.0 : AiDock.PAD;
        this.setPadding(new Insets(0, 0, 0, paddingLeft));

        timeline.getKeyFrames().add(new KeyFrame(Duration.millis(200),
                new KeyValue(this.prefWidthProperty(), targetWidth),
                new KeyValue(this.maxWidthProperty(), targetWidth),
                new KeyValue(textLabel.opacityProperty(), textOpacity)
        ));

        timeline.play();
    }

    private Background createBackground(Color color) {
        return new Background(new BackgroundFill(color, new CornerRadii(AiDock.ITEM_HEIGHT), Insets.EMPTY));
    }
}