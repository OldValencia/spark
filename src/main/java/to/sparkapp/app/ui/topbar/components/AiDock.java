package to.sparkapp.app.ui.topbar.components;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.layout.Priority;
import lombok.Getter;
import lombok.Setter;
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
import java.util.function.Consumer;
import javafx.scene.image.Image;

@Slf4j
public class AiDock extends StackPane {

    public static final Map<String, Image> ICON_CACHE = new ConcurrentHashMap<>();
    public static final int ICON_SIZE = 18;

    static final int ITEM_HEIGHT = 32;
    /** Item plus four logical pixels of vertical padding on each side. */
    static final int DOCK_HEIGHT = ITEM_HEIGHT + 8;
    static final int ITEM_MARGIN = 6;

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

    @Getter
    private DockItemNode selectedNode = null;
    private Consumer<Color> onAccentChanged;
    @Setter
    private Runnable onOrderChanged;

    public void setOnAccentChanged(Consumer<Color> callback) { onAccentChanged = callback; updateTopBarColor(); }

    public void orderChanged() { if (onOrderChanged != null) onOrderChanged.run(); }
    public boolean isReducedMotion() { return appPreferences.isReducedMotion(); }

    public String getSelectedId() { return selectedNode == null ? null : selectedNode.getConfig().id(); }

    public void selectSite(String id) {
        dockItems.stream().filter(item -> item.getConfig().id().equals(id)).findFirst().ifPresent(this::selectItem);
    }

    public void revealSelection() {
        Platform.runLater(() -> {
            if (selectedNode == null) return;
            dockContainer.applyCss(); dockContainer.layout();
            double overflow = dockContainer.getWidth() - scrollPane.getViewportBounds().getWidth();
            if (overflow <= 0) return;
            double left = scrollPane.getHvalue() * overflow, x = selectedNode.getLayoutX();
            if (x < left) scrollPane.setHvalue(Math.clamp(x / overflow, 0, 1));
            else if (x + selectedNode.getWidth() > left + scrollPane.getViewportBounds().getWidth())
                scrollPane.setHvalue(Math.clamp((x + selectedNode.getWidth() - scrollPane.getViewportBounds().getWidth()) / overflow, 0, 1));
        });
    }

    public double desiredWidth() {
        double total = dockItems.stream().mapToDouble(DockItemNode::getExpandedWidth).sum()
                + Math.max(0, dockItems.size() - 1) * ITEM_MARGIN;
        double budget = Math.min(MAX_DOCK_WIDTH, dockItems.stream().limit(4).mapToDouble(DockItemNode::getExpandedWidth).sum()
                + Math.max(0, Math.min(4, dockItems.size()) - 1) * ITEM_MARGIN + 40);
        return Math.min(total, budget);
    }

    public void reorder(String id, String target, boolean after) {
        if (id.equals(target)) return;
        var from = dockItems.stream().filter(item -> item.getConfig().id().equals(id)).findFirst().orElse(null);
        var to = dockItems.stream().filter(item -> item.getConfig().id().equals(target)).findFirst().orElse(null);
        if (from == null || to == null) return;
        dockItems.remove(from);
        dockItems.add(dockItems.indexOf(to) + (after ? 1 : 0), from);
        dockContainer.getChildren().setAll(dockItems);
        AiDockOrderUtils.saveCurrentOrder(dockItems, appPreferences);
        orderChanged();
    }

    public void dispose() {
        cancelInteraction();
        prefWidthProperty().unbind(); minWidthProperty().unbind(); maxWidthProperty().unbind();
        dockItems.forEach(DockItemNode::dispose);
    }

    public void cancelInteraction() {
        dragManager.cancel();
        if (leftArrow instanceof AiDockScrollArrow arrow) arrow.cancel();
        if (rightArrow instanceof AiDockScrollArrow arrow) arrow.cancel();
    }

    @Getter
    private final AiDockDragManager dragManager;

    public AiDock(List<AiConfiguration.AiConfig> configs, FxWebViewPane fxWebViewPane, AppPreferences appPreferences) {
        this(configs, fxWebViewPane, appPreferences, null);
    }

    public AiDock(List<AiConfiguration.AiConfig> configs, FxWebViewPane fxWebViewPane, AppPreferences appPreferences, String activeId) {
        this.fxWebViewPane = fxWebViewPane;
        this.appPreferences = appPreferences;
        this.dragManager = new AiDockDragManager(this, appPreferences);

        var userIconsDir = new File(AppPaths.DATA_DIR, "icons");

        this.setAlignment(Pos.CENTER_LEFT);
        this.setMaxWidth(MAX_DOCK_WIDTH);
        this.setMinWidth(0);
        this.setMinHeight(DOCK_HEIGHT);
        this.setPrefHeight(DOCK_HEIGHT);
        this.setMaxHeight(DOCK_HEIGHT);
        this.setStyle("-fx-background-color: transparent;");

        scrollPane = new ScrollPane();
        scrollPane.getStyleClass().add("shell-dock");
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setFitToHeight(true);
        scrollPane.setPannable(false);
        scrollPane.setMaxWidth(MAX_DOCK_WIDTH);
        scrollPane.setMinWidth(0);
        scrollPane.setMinHeight(DOCK_HEIGHT);
        scrollPane.setPrefHeight(DOCK_HEIGHT);
        scrollPane.setMaxHeight(DOCK_HEIGHT);

        dockContainer = new HBox();
        dockContainer.setSpacing(ITEM_MARGIN);
        dockContainer.setStyle("-fx-background-color: transparent;");
        dockContainer.setPadding(new Insets(4, 0, 4, 0));
        scrollPane.setContent(dockContainer);

        scrollPane.addEventFilter(ScrollEvent.SCROLL, e -> {
            double delta = Math.abs(e.getDeltaX()) > Math.abs(e.getDeltaY()) ? e.getDeltaX() : e.getDeltaY();
            if (delta != 0) {
                double overflow = dockContainer.getWidth() - scrollPane.getViewportBounds().getWidth();
                if (overflow > 0) scrollPane.setHvalue(Math.clamp(scrollPane.getHvalue() - delta / overflow, 0.0, 1.0));
            }
            e.consume();
        });

        leftArrow  = new AiDockScrollArrow(true, this);
        rightArrow = new AiDockScrollArrow(false, this);

        leftArrow.managedProperty().bind(leftArrow.visibleProperty());
        rightArrow.managedProperty().bind(rightArrow.visibleProperty());
        var rail = new HBox(leftArrow, scrollPane, rightArrow);
        rail.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(scrollPane, Priority.ALWAYS);
        getChildren().add(rail);
        scrollPane.hvalueProperty().addListener((obs, old, value) -> refreshArrowOpacity());
        dockContainer.widthProperty().addListener((obs, old, value) -> refreshArrowOpacity());
        widthProperty().addListener((obs, old, value) -> refreshArrowOpacity());
        scrollPane.viewportBoundsProperty().addListener((obs, old, value) -> refreshArrowOpacity());

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
        if (activeId != null) {
            for (int i = 0; i < orderedConfigs.size(); i++) {
                if (orderedConfigs.get(i).id().equals(activeId)) { initialIndex = i; initialUrl = null; break; }
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
        return dockContainer.getWidth() > getWidth() + 2;
    }

    private boolean canScrollLeft() {
        return scrollPane.getHvalue() > 0.005;
    }

    private boolean canScrollRight() {
        return hasOverflow() && scrollPane.getHvalue() < 0.995;
    }

    public void refreshArrowOpacity() {
        boolean overflow = hasOverflow();
        leftArrow.setVisible(overflow); rightArrow.setVisible(overflow);
        leftArrow.setDisable(!canScrollLeft()); rightArrow.setDisable(!canScrollRight());
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
        revealSelection();
    }

    private void updateTopBarColor() {
        Platform.runLater(() -> {
            if (selectedNode == null) return;
            if (onAccentChanged != null) {
                try { onAccentChanged.accept(Color.web(selectedNode.getConfig().color())); }
                catch (IllegalArgumentException | NullPointerException ignored) { onAccentChanged.accept(Color.web("#bcbcc6")); }
                return;
            }
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
