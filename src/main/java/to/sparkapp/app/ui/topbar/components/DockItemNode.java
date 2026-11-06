package to.sparkapp.app.ui.topbar.components;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.util.Duration;
import lombok.Getter;
import to.sparkapp.app.config.AiConfiguration;
import to.sparkapp.app.ui.Theme;

/** A stable tab: hovering and selecting never change its width. */
public class DockItemNode extends HBox {
    @Getter private final AiConfiguration.AiConfig config;
    @Getter private final double expandedWidth;
    @Getter private boolean isDragging;
    private final AiDock dock;
    private boolean selected, pressed;
    private double pressX, pressY, dragStartLocalX;

    public DockItemNode(AiConfiguration.AiConfig config, AiDock dock) {
        this.config = config; this.dock = dock;
        getStyleClass().add("site-tab");
        setAlignment(Pos.CENTER_LEFT); setSpacing(7); setPadding(new Insets(0, 9, 0, 9));
        setCursor(Cursor.HAND); setFocusTraversable(true); setAccessibleText(config.name());
        Tooltip.install(this, ShellTooltip.create(config.name()));
        var image = config.icon() == null ? null : AiDock.ICON_CACHE.get(config.icon());
        if (image != null && image.getWidth() > 1) {
            var icon = new ImageView(image); icon.setFitWidth(17); icon.setFitHeight(17); icon.setPreserveRatio(true);
            getChildren().add(icon);
        } else {
            var icon = new Label("↗"); icon.setMinWidth(17); icon.setTextFill(Theme.TEXT_SECONDARY); getChildren().add(icon);
        }
        var name = new Label(config.name()); name.setFont(Font.font(Theme.FONT_NAME, 12));
        name.setTextFill(Color.web("#b9b9c3")); name.setMinWidth(0);
        getChildren().add(name);
        var measured = new Text(config.name()); measured.setFont(name.getFont());
        expandedWidth = 18 + 17 + 7 + 4 + Math.ceil(Math.min(112, measured.getLayoutBounds().getWidth()));
        setMinWidth(expandedWidth); setPrefWidth(expandedWidth); setMaxWidth(expandedWidth);
        setMinHeight(32); setPrefHeight(32); setMaxHeight(32);
        setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE) { dock.selectItem(this); e.consume(); }
        });
        setOnMousePressed(e -> {
            if (e.getButton() != MouseButton.PRIMARY) return;
            pressed = true; isDragging = false;
            pressX = e.getScreenX(); pressY = e.getScreenY();
            dragStartLocalX = dock.getDockContainer().sceneToLocal(e.getSceneX(), e.getSceneY()).getX();
            e.consume();
        });
        setOnMouseDragged(e -> {
            if (!pressed || !e.isPrimaryButtonDown()) return;
            if (!isDragging && Math.hypot(e.getScreenX() - pressX, e.getScreenY() - pressY) < 5) return;
            if (!isDragging) { isDragging = true; dock.getDragManager().onDragStart(this); }
            updateDragPosition(e.getSceneX());
            dock.getDragManager().startAutoscrollIfNeeded(e.getSceneX()); e.consume();
        });
        setOnMouseReleased(e -> {
            if (e.getButton() != MouseButton.PRIMARY || !pressed) return;
            pressed = false; dock.getDragManager().stopAutoscroll();
            if (isDragging) {
                isDragging = false; dock.getDragManager().onDragEnd(this);
                var animation = new Timeline(new KeyFrame(Duration.millis(dock.isReducedMotion() ? 1 : 120),
                        new KeyValue(translateXProperty(), 0)));
                getProperties().put("dragAnim", animation); animation.play();
            } else dock.selectItem(this);
            e.consume();
        });
        refreshState();
    }

    public void updateDragPosition(double sceneX) {
        double x = dock.getDockContainer().sceneToLocal(sceneX, 0).getX() - dragStartLocalX;
        setTranslateX(x); dock.getDragManager().onDrag(getLayoutX() + x + getWidth() / 2);
    }

    public void setSelected(boolean selected) { this.selected = selected; refreshState(); }

    public void refreshState() {
        ((Label) getChildren().getLast()).setTextFill(Color.web(selected ? "#eeeeF3" : "#b9b9c3"));
        if (!selected) { setStyle(""); return; }
        Color accent = Color.web("#bcbcc6");
        try { accent = Color.web(config.color()); } catch (RuntimeException ignored) {}
        String wash = Theme.toHex(Theme.lerp(Color.web("#25262a"), accent, .13));
        String border = Theme.toHex(Theme.lerp(Color.web("#25262a"), accent, .30));
        setStyle("-fx-background-color:" + wash + ";-fx-border-color:" + border + ";");
    }

    public void cancelDrag() {
        pressed = false; isDragging = false;
        var animation = (Timeline) getProperties().remove("dragAnim");
        if (animation != null) animation.stop();
        setTranslateX(0); setViewOrder(0);
    }

    public void dispose() { cancelDrag(); }
}
