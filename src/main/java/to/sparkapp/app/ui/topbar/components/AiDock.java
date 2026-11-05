package to.sparkapp.app.ui.topbar.components;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import to.sparkapp.app.config.AiConfiguration;
import to.sparkapp.app.config.AppPaths;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.ui.Theme;
import to.sparkapp.app.ui.topbar.utils.AiDockDragManager;
import to.sparkapp.app.ui.topbar.utils.AiDockIconUtils;
import to.sparkapp.app.ui.topbar.utils.AiDockOrderUtils;
import to.sparkapp.app.ui.webview.FxWebViewPane;
import to.sparkapp.app.utils.UrlUtils;

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
    /** Item plus the 8 px breathing room above and below it. */
    static final int DOCK_HEIGHT = ITEM_HEIGHT + 16;
    static final int ITEM_MARGIN = 6;
    static final int PAD = 12;
    static final int GAP = 8;

    private static final int MAX_DOCK_WIDTH = 500;

    @Getter
    private final HBox dockContainer;
    @Getter
    private final ScrollPane scrollPane;
    @Getter
    private final List<DockItemNode> dockItems = new ArrayList<>();
    private final FxWebViewPane fxWebViewPane;
    private final AppPreferences appPreferences;

    private final StackPane leftArrow;
    private final StackPane rightArrow;
    private final Timeline arrowFadeTimeline = new Timeline();

    @Getter
    private DockItemNode selectedNode = null;
    private boolean isDockHovered = false;

    @Getter
    private final AiDockDragManager dragManager;

    public AiDock(List<AiConfiguration.AiConfig> configs, FxWebViewPane fxWebViewPane, AppPreferences appPreferences) {
        this.fxWebViewPane = fxWebViewPane;
        this.appPreferences = appPreferences;
        this.dragManager = new AiDockDragManager(this, appPreferences);

        var userIconsDir = new File(AppPaths.DATA_DIR, "icons");

        this.setAlignment(Pos.CENTER_LEFT);
        this.setMaxWidth(MAX_DOCK_WIDTH);
        this.setMinHeight(DOCK_HEIGHT);
        this.setPrefHeight(DOCK_HEIGHT);
        this.setMaxHeight(DOCK_HEIGHT);
        this.setStyle("-fx-background-color: transparent;");

        scrollPane = new ScrollPane();
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setFitToHeight(true);
        scrollPane.setPannable(false);
        scrollPane.setMaxWidth(MAX_DOCK_WIDTH);
        scrollPane.setMinHeight(DOCK_HEIGHT);
        scrollPane.setPrefHeight(DOCK_HEIGHT);
        scrollPane.setMaxHeight(DOCK_HEIGHT);

        dockContainer = new HBox();
        dockContainer.setSpacing(ITEM_MARGIN);
        dockContainer.setStyle("-fx-background-color: transparent;");
        dockContainer.setPadding(new Insets(8, 0, 8, 0));
        scrollPane.setContent(dockContainer);

        scrollPane.addEventFilter(ScrollEvent.SCROLL, e -> {
            double delta = e.getDeltaY();
            if (delta != 0) {
                scrollPane.setHvalue(Math.clamp(scrollPane.getHvalue() - delta / 200.0, 0.0, 1.0));
                e.consume();
            }
        });

        leftArrow  = new AiDockScrollArrow(true, this);
        rightArrow = new AiDockScrollArrow(false, this);

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
        String initialUrl = null;

        // The remembered URL is usually a page inside a provider (a conversation),
        // so the provider is matched by host rather than by an exact URL match.
        if (lastUrl != null) {
            for (int i = 0; i < orderedConfigs.size(); i++) {
                var configUrl = orderedConfigs.get(i).url();
                if (configUrl.equals(lastUrl)
                        || UrlUtils.matchingBaseUrl(lastUrl, List.of(configUrl)) != null) {
                    initialIndex = i;
                    initialUrl = lastUrl;
                    break;
                }
            }
        }

        if (appPreferences.isRememberLastAi() && appPreferences.getLastSiteId() != null) {
            for (int i = 0; i < orderedConfigs.size(); i++) {
                if (orderedConfigs.get(i).id().equals(appPreferences.getLastSiteId())) {
                    initialIndex = i;
                    initialUrl = appPreferences.getSiteUrl(orderedConfigs.get(i));
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

        if (!dockItems.isEmpty()) selectItem(dockItems.get(initialIndex), initialUrl);
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

    public void refreshArrowOpacity() {
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

    public static void clearIconCache() {
        ICON_CACHE.clear();
        log.debug("Icon cache cleared");
    }

    public static void pruneIconCache(List<String> activeIcons) {
        ICON_CACHE.keySet().retainAll(activeIcons);
        log.info("Icon cache pruned, {} icons remaining", ICON_CACHE.size());
    }

    public void selectItem(DockItemNode node) {
        selectItem(node, null);
    }

    /**
     * @param targetUrl page of the provider to open instead of its base URL, or {@code null}
     */
    private void selectItem(DockItemNode node, String targetUrl) {
        if (selectedNode == node) return;
        if (selectedNode != null) selectedNode.setSelected(false);
        selectedNode = node;
        selectedNode.setSelected(true);
        fxWebViewPane.setCurrentConfig(node.getConfig(), targetUrl);
        updateTopBarColor();
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
