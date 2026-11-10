package to.sparkapp.app.ui.topbar.components;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
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
import to.sparkapp.app.config.AiConfiguration.AiConfig;
import to.sparkapp.app.ui.Theme;

/**
 * A stable tab: hovering and selecting never change its width.
 */
public class DockItemNode extends HBox {
    /** Must match `.site-tab .label` in shell.css, because the tab width is measured with it. */
    private static final double TAB_FONT_SIZE = 13;
    @Getter
    private final AiConfig config;
    @Getter
    private final double expandedWidth;
    @Getter
    private boolean isDragging;
    private final AiDock dock;
    private final Tooltip tooltip;
    private final TabIconMarks iconMarks;
    private boolean selected;
    private boolean pressed;

    private double pressX;
    private double pressY;
    private double dragStartLocalX;

    public DockItemNode(AiConfig config, AiDock dock) {
        this.config = config;
        this.dock = dock;
        getStyleClass().add("site-tab");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(7);
        setPadding(new Insets(0, 9, 0, 9));
        setCursor(Cursor.HAND);
        setFocusTraversable(true);
        setAccessibleText(config.name());
        tooltip = ShellTooltip.create(config.name());
        Tooltip.install(this, tooltip);

        iconMarks = new TabIconMarks(createIcon(config), dock::isReducedMotion);
        getChildren().add(iconMarks);

        var name = new Label(config.name());
        name.setFont(Font.font(Theme.FONT_NAME, TAB_FONT_SIZE));
        name.setTextFill(Color.web("#b9b9c3"));
        name.setMinWidth(0);
        getChildren().add(name);

        var measured = new Text(config.name());
        measured.setFont(name.getFont());
        expandedWidth = 18 + 17 + 7 + 4 + Math.ceil(Math.min(112, measured.getLayoutBounds().getWidth()));
        setMinWidth(expandedWidth);
        setPrefWidth(expandedWidth);
        setMaxWidth(expandedWidth);
        setMinHeight(32);
        setPrefHeight(32);
        setMaxHeight(32);
        setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE) {
                dock.selectItem(this);
                e.consume();
            }
        });
        setOnContextMenuRequested(e -> {
            dock.openTabMenu(this);
            e.consume();
        });
        setOnMousePressed(e -> {
            if (e.getButton() != MouseButton.PRIMARY) {
                return;
            }

            pressed = true;
            isDragging = false;
            pressX = e.getScreenX();
            pressY = e.getScreenY();
            dragStartLocalX = dock.getDockContainer().sceneToLocal(e.getSceneX(), e.getSceneY()).getX();
            e.consume();
        });
        setOnMouseDragged(e -> {
            if (!pressed || !e.isPrimaryButtonDown()) {
                return;
            }

            if (!isDragging && Math.hypot(e.getScreenX() - pressX, e.getScreenY() - pressY) < 5) {
                return;
            }

            if (!isDragging) {
                isDragging = true;
                dock.getDragManager().onDragStart(this);
            }

            updateDragPosition(e.getSceneX());
            dock.getDragManager().startAutoscrollIfNeeded(e.getSceneX());
            e.consume();
        });
        setOnMouseReleased(e -> {
            if (e.getButton() != MouseButton.PRIMARY || !pressed) {
                return;
            }

            pressed = false;
            dock.getDragManager().stopAutoscroll();

            if (isDragging) {
                isDragging = false;
                dock.getDragManager().onDragEnd(this);

                var animation = new Timeline(new KeyFrame(Duration.millis(dock.isReducedMotion() ? 1 : 120),
                        new KeyValue(translateXProperty(), 0)));
                getProperties().put("dragAnim", animation);
                animation.play();
            } else {
                dock.selectItem(this);
            }

            e.consume();
        });
        refreshState();
    }

    private static Node createIcon(AiConfig config) {
        var image = config.icon() == null ? null : AiDock.ICON_CACHE.get(config.icon());

        if (image != null && image.getWidth() > 1) {
            var icon = new ImageView(image);
            icon.setFitWidth(17);
            icon.setFitHeight(17);
            icon.setPreserveRatio(true);

            return icon;
        }

        var icon = new Label("↗");
        icon.setMinWidth(17);
        icon.setTextFill(Theme.TEXT_SECONDARY);

        return icon;
    }

    /** Marks a site that changed while another site was open. */
    public void setUpdated(boolean updated) {
        if (updated) {
            iconMarks.show();
        } else {
            iconMarks.hide();
        }

        refreshAccessibleText();
    }

    public boolean isUpdated() {
        return iconMarks.isShown();
    }

    /** Shows a speaker while the site plays sound, crossed out when the site is muted. */
    public void showSound(boolean playing, boolean muted) {
        iconMarks.sound().show(playing, muted);
        refreshAccessibleText();
    }

    public boolean isShowingSound() {
        return iconMarks.sound().isShowingSound();
    }

    public boolean isShowingMuted() {
        return iconMarks.sound().isShowingMuted();
    }

    private void refreshAccessibleText() {
        var text = config.name();

        if (isShowingSound()) {
            text += ", playing sound";
        } else if (isShowingMuted()) {
            text += ", muted";
        }

        if (isUpdated()) {
            text += ", updated";
        }

        setAccessibleText(text);
    }

    public void updateDragPosition(double sceneX) {
        double x = dock.getDockContainer().sceneToLocal(sceneX, 0).getX() - dragStartLocalX;
        setTranslateX(x);
        dock.getDragManager().onDrag(getLayoutX() + x + getWidth() / 2);
    }

    /** Shows the site's keyboard shortcut, such as Ctrl+2, next to its name in the tooltip. */
    public void setShortcutHint(String shortcut) {
        tooltip.setText(shortcut == null ? config.name() : config.name() + " · " + shortcut);
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
        refreshState();
    }

    /** The selected tab's highlight is drawn by the dock, so it can slide between tabs. */
    public void refreshState() {
        ((Label) getChildren().getLast()).setTextFill(Color.web(selected ? "#eeeeF3" : "#b9b9c3"));
    }

    public void cancelDrag() {
        pressed = false;
        isDragging = false;

        var animation = (Timeline) getProperties().remove("dragAnim");

        if (animation != null) {
            animation.stop();
        }

        setTranslateX(0);
        setViewOrder(0);
    }

    public void dispose() {
        cancelDrag();
    }
}
