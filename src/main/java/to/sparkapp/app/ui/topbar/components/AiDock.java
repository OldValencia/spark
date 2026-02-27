package to.sparkapp.app.ui.topbar.components;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import lombok.extern.slf4j.Slf4j;
import to.sparkapp.app.config.AiConfiguration;
import to.sparkapp.app.config.AppPaths;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.ui.Theme;
import to.sparkapp.app.ui.topbar.utils.AiDockIconUtils;
import to.sparkapp.app.ui.topbar.utils.AiDockOrderUtils;
import to.sparkapp.app.ui.webview.FxWebViewPane;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class AiDock extends StackPane {

    public static final Map<String, javafx.scene.image.Image> ICON_CACHE = new ConcurrentHashMap<>();
    public static final int ICON_SIZE = 24;

    static final int ITEM_HEIGHT = 32;
    static final int ITEM_MARGIN = 6;
    static final int PAD = 12;
    static final int GAP = 8;

    private static final int MAX_DOCK_WIDTH = 500;
    private static final int ARROW_WIDTH = 22;
    private static final double SCROLL_STEP = 0.12;
    private static final double HOLD_SCROLL_RATE = 0.010;

    private final HBox dockContainer;
    private final ScrollPane scrollPane;
    private final List<DockItemNode> dockItems = new ArrayList<>();
    private final FxWebViewPane fxWebViewPane;
    private final AppPreferences appPreferences;

    private final StackPane leftArrow;
    private final StackPane rightArrow;
    private final Timeline arrowFadeTimeline = new Timeline();

    private DockItemNode selectedNode = null;
    private boolean isDockHovered = false;

    public AiDock(List<AiConfiguration.AiConfig> configs, FxWebViewPane fxWebViewPane, AppPreferences appPreferences) {
        this.fxWebViewPane = fxWebViewPane;
        this.appPreferences = appPreferences;

        var userIconsDir = new File(AppPaths.DATA_DIR, "icons");

        this.setAlignment(Pos.CENTER_LEFT);
        this.setMaxWidth(MAX_DOCK_WIDTH);
        this.setStyle("-fx-background-color: transparent;");

        scrollPane = new ScrollPane();
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setFitToHeight(true);
        scrollPane.setPannable(false);
        scrollPane.setMaxWidth(MAX_DOCK_WIDTH);

        dockContainer = new HBox();
        dockContainer.setSpacing(ITEM_MARGIN);
        dockContainer.setStyle("-fx-background-color: transparent;");
        dockContainer.setPadding(new Insets(8, 0, 8, 0));
        scrollPane.setContent(dockContainer);

        scrollPane.addEventFilter(ScrollEvent.SCROLL, e -> {
            double delta = e.getDeltaY();
            if (delta != 0) {
                scrollPane.setHvalue(Math.max(0.0, Math.min(1.0, scrollPane.getHvalue() - delta / 200.0)));
                e.consume();
            }
        });

        leftArrow  = createArrow(true);
        rightArrow = createArrow(false);

        var arrowOverlay = new AnchorPane(leftArrow, rightArrow);
        arrowOverlay.setPickOnBounds(false);
        AnchorPane.setLeftAnchor(leftArrow, 0.0);
        AnchorPane.setTopAnchor(leftArrow, 3.0);
        AnchorPane.setBottomAnchor(leftArrow, 3.0);
        AnchorPane.setRightAnchor(rightArrow, 0.0);
        AnchorPane.setTopAnchor(rightArrow, 3.0);
        AnchorPane.setBottomAnchor(rightArrow, 3.0);

        leftArrow.setOpacity(0.0);
        rightArrow.setOpacity(0.0);

        this.getChildren().addAll(scrollPane, arrowOverlay);

        this.setOnMouseEntered(e -> {
            isDockHovered = true;
            for (var item : dockItems) item.setDockHovered(true);
            Platform.runLater(this::updateArrowVisibility);
        });
        this.setOnMouseExited(e -> {
            isDockHovered = false;
            for (var item : dockItems) item.setDockHovered(false);
            fadeArrows(0.0, 0.0);
        });

        scrollPane.hvalueProperty().addListener((obs, old, val) -> {
            if (isDockHovered) refreshArrowOpacity();
        });
        dockContainer.widthProperty().addListener((obs, old, val) -> {
            if (isDockHovered) Platform.runLater(this::updateArrowVisibility);
        });

        var orderedConfigs = AiDockOrderUtils.applyCustomOrder(configs, appPreferences);
        var lastUrl = appPreferences.getLastUrl();
        int initialIndex = 0;

        if (lastUrl != null) {
            for (int i = 0; i < orderedConfigs.size(); i++) {
                if (orderedConfigs.get(i).url().equals(lastUrl)) {
                    initialIndex = i;
                    break;
                }
            }
        }

        for (var config : orderedConfigs) {
            AiDockIconUtils.preloadIcon(config, userIconsDir);
            var node = new DockItemNode(config, this);
            dockItems.add(node);
            dockContainer.getChildren().add(node);
        }

        if (!dockItems.isEmpty()) selectItem(dockItems.get(initialIndex));
    }

    private boolean hasOverflow() {
        return dockContainer.getWidth() > scrollPane.getWidth() + 2;
    }

    private boolean canScrollLeft() {
        return scrollPane.getHvalue() > 0.005;
    }

    private boolean canScrollRight() {
        return hasOverflow() && scrollPane.getHvalue() < 0.995;
    }

    private void updateArrowVisibility() {
        if (!isDockHovered || !hasOverflow()) {
            fadeArrows(0.0, 0.0);
            return;
        }
        fadeArrows(
                canScrollLeft()  ? 1.0 : 0.25,
                canScrollRight() ? 1.0 : 0.25
        );
    }

    private void refreshArrowOpacity() {
        if (!isDockHovered || !hasOverflow()) return;
        leftArrow.setOpacity(canScrollLeft()  ? 1.0 : 0.25);
        rightArrow.setOpacity(canScrollRight() ? 1.0 : 0.25);
    }

    private void fadeArrows(double leftTarget, double rightTarget) {
        arrowFadeTimeline.stop();
        arrowFadeTimeline.getKeyFrames().clear();
        arrowFadeTimeline.getKeyFrames().add(new KeyFrame(Duration.millis(180),
                new KeyValue(leftArrow.opacityProperty(), leftTarget),
                new KeyValue(rightArrow.opacityProperty(), rightTarget)
        ));
        arrowFadeTimeline.play();
    }

    private StackPane createArrow(boolean isLeft) {
        var container = new StackPane();
        container.setPrefWidth(ARROW_WIDTH);
        container.setMinWidth(ARROW_WIDTH);
        container.setMaxWidth(ARROW_WIDTH);
        container.setAlignment(Pos.CENTER);
        container.setCursor(Cursor.HAND);

        var bg = new Rectangle();
        bg.setArcWidth(8);
        bg.setArcHeight(8);
        bg.setFill(Theme.BG_HOVER);

        var tri = new Polygon();
        if (isLeft) {
            tri.getPoints().addAll(-3.5, 0.0, 3.5, -5.0, 3.5, 5.0);
        } else {
            tri.getPoints().addAll(3.5, 0.0, -3.5, -5.0, -3.5, 5.0);
        }
        tri.setFill(Theme.TEXT_SECONDARY);

        container.getChildren().addAll(bg, tri);

        container.heightProperty().addListener((obs, old, h) -> {
            bg.setWidth(ARROW_WIDTH);
            bg.setHeight(h.doubleValue());
        });

        container.setOnMouseEntered(e -> tri.setFill(Theme.TEXT_PRIMARY));
        container.setOnMouseExited(e -> tri.setFill(Theme.TEXT_SECONDARY));

        var holdTimeline = new Timeline(new KeyFrame(Duration.millis(16), e -> {
            double step = isLeft ? -HOLD_SCROLL_RATE : HOLD_SCROLL_RATE;
            scrollPane.setHvalue(Math.max(0.0, Math.min(1.0, scrollPane.getHvalue() + step)));
            refreshArrowOpacity();
        }));
        holdTimeline.setCycleCount(Animation.INDEFINITE);

        var holdDelay = new Timeline(new KeyFrame(Duration.millis(350), e -> holdTimeline.play()));

        container.setOnMousePressed(e -> {
            double step = isLeft ? -SCROLL_STEP : SCROLL_STEP;
            scrollPane.setHvalue(Math.max(0.0, Math.min(1.0, scrollPane.getHvalue() + step)));
            refreshArrowOpacity();
            holdDelay.playFromStart();
            e.consume();
        });
        container.setOnMouseReleased(e -> {
            holdDelay.stop();
            holdTimeline.stop();
        });
        container.addEventHandler(MouseEvent.MOUSE_EXITED, e -> {
            holdDelay.stop();
            holdTimeline.stop();
            tri.setFill(Theme.TEXT_SECONDARY);
        });

        return container;
    }

    public static void clearIconCache() {
        ICON_CACHE.clear();
        log.debug("Icon cache cleared");
    }

    public static void pruneIconCache(List<String> activeIcons) {
        ICON_CACHE.keySet().retainAll(activeIcons);
        log.info("Icon cache pruned, {} icons remaining", ICON_CACHE.size());
    }

    public void selectItem(DockItemNode node) {
        if (selectedNode == node) return;
        if (selectedNode != null) selectedNode.setSelected(false);
        selectedNode = node;
        selectedNode.setSelected(true);
        fxWebViewPane.setCurrentConfig(node.getConfig());
        if (appPreferences != null) appPreferences.setLastUrl(node.getConfig().url());
        updateTopBarColor();
    }

    public void handleDrag(DockItemNode draggedNode, double sceneX) {
        var children = dockContainer.getChildren();
        var currentIndex = children.indexOf(draggedNode);
        var targetIndex = currentIndex;

        for (int i = 0; i < children.size(); i++) {
            var child = children.get(i);
            var bounds = child.localToScene(child.getBoundsInLocal());
            var centerX = bounds.getMinX() + bounds.getWidth() / 2;
            if (sceneX < centerX) {
                targetIndex = i;
                break;
            } else if (i == children.size() - 1) {
                targetIndex = i;
            }
        }

        if (targetIndex != currentIndex) {
            children.remove(currentIndex);
            children.add(targetIndex, draggedNode);
            dockItems.clear();
            for (var child : children) {
                if (child instanceof DockItemNode din) dockItems.add(din);
            }
            AiDockOrderUtils.saveCurrentOrder(dockItems, appPreferences);
        }
    }

    private void updateTopBarColor() {
        Platform.runLater(() -> {
            var parent = this.getParent();
            while (parent != null && !(parent instanceof GradientPanel)) {
                parent = parent.getParent();
            }
            if (parent instanceof GradientPanel gradientPanel) {
                var accent = Theme.ACCENT;
                try {
                    if (selectedNode.getConfig().color() != null) {
                        accent = Color.web(selectedNode.getConfig().color());
                    }
                } catch (Exception e) {
                    log.warn("Warning while trying to update topBarColor", e);
                }
                gradientPanel.updateAccentColor(accent);
            }
        });
    }
}
