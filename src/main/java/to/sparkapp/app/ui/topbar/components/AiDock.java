package to.sparkapp.app.ui.topbar.components;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import to.sparkapp.app.config.AiConfiguration.AiConfig;
import to.sparkapp.app.config.AppPaths;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.config.MuteRule;
import to.sparkapp.app.browser.SiteShortcut;
import to.sparkapp.app.ui.SiteAccent;
import to.sparkapp.app.ui.topbar.utils.AiDockDragManager;
import to.sparkapp.app.ui.topbar.utils.AiDockIconUtils;
import to.sparkapp.app.ui.topbar.utils.AiDockOrderUtils;
import to.sparkapp.app.ui.topbar.utils.SiteOrderDiagnostics;
import to.sparkapp.app.ui.webview.FxWebViewPane;

@Slf4j
public class AiDock extends StackPane {

    public static final Map<String, Image> ICON_CACHE = new ConcurrentHashMap<>();
    public static final int ICON_SIZE = 18;

    static final int ITEM_HEIGHT = 32;

    /**
     * Item plus four logical pixels of vertical padding on each side.
     */
    static final int DOCK_HEIGHT = ITEM_HEIGHT + 8;

    static final int ITEM_MARGIN = 6;

    static final int MAX_DOCK_WIDTH = 500;

    @Getter
    private final HBox dockContainer;
    @Getter
    private final ScrollPane scrollPane;
    @Getter
    private final List<DockItemNode> dockItems = new ArrayList<>();
    private final FxWebViewPane fxWebViewPane;
    private final AppPreferences appPreferences;

    private final AiDockViewport viewport;

    @Getter
    private DockItemNode selectedNode = null;
    private Consumer<Color> onAccentChanged;
    @Setter
    private Runnable onSiteSelected;
    /** Opens the menu of a tab after a right-click or the keyboard's menu key. */
    @Setter
    private Consumer<DockItemNode> onTabMenu;
    private final List<Runnable> orderListeners = new ArrayList<>();

    public void setOnAccentChanged(Consumer<Color> callback) {
        onAccentChanged = callback;
        updateTopBarColor();
    }

    /** Runs after the user reorders the sites. */
    public void addOrderListener(Runnable listener) {
        orderListeners.add(listener);
    }

    public void orderChanged() {
        SiteOrderDiagnostics.record(this);
        orderListeners.forEach(Runnable::run);
    }

    public boolean isReducedMotion() {
        return appPreferences.isReducedMotion();
    }

    public String getSelectedId() {
        return selectedNode == null ? null : selectedNode.getConfig().id();
    }

    /** The site on screen, or {@code null} before one is chosen. */
    public AiConfig getSelectedSite() {
        return selectedNode == null ? null : selectedNode.getConfig();
    }

    public void selectSite(String id) {
        dockItems.stream().filter(item -> item.getConfig().id().equals(id)).findFirst().ifPresent(this::selectItem);
    }

    public void revealSelection() {
        viewport.revealSelection(selectedNode);
    }

    public void refreshArrowOpacity() {
        viewport.refreshArrowOpacity();
    }

    public double desiredWidth() {
        double total = dockItems.stream().mapToDouble(DockItemNode::getExpandedWidth).sum() +
                Math.max(0, dockItems.size() - 1) * ITEM_MARGIN;
        double budget = Math.min(MAX_DOCK_WIDTH,
                dockItems.stream().limit(4).mapToDouble(DockItemNode::getExpandedWidth).sum() +
                        Math.max(0, Math.min(4, dockItems.size()) - 1) * ITEM_MARGIN + 40);

        return Math.min(total, budget);
    }

    public void reorder(String id, String target, boolean after) {
        if (id.equals(target)) {
            return;
        }

        var from = dockItems.stream().filter(item -> item.getConfig().id().equals(id)).findFirst().orElse(null);
        var to = dockItems.stream().filter(item -> item.getConfig().id().equals(target)).findFirst().orElse(null);

        if (from == null || to == null) {
            return;
        }

        log.info("Reordering site: from={}, target={}, after={}", dockItems.indexOf(from), dockItems.indexOf(to), after);
        dockItems.remove(from);
        dockItems.add(dockItems.indexOf(to) + (after ? 1 : 0), from);
        dockContainer.getChildren().setAll(dockItems);
        refreshShortcutHints();
        AiDockOrderUtils.saveCurrentOrder(dockItems, appPreferences);
        orderChanged();
    }

    /** Moves a site one place to the left (-1) or to the right (+1). */
    public void move(String id, int offset) {
        var index = indexOf(id);
        var target = index + offset;

        if (index < 0 || target < 0 || target >= dockItems.size()) {
            return;
        }

        reorder(id, dockItems.get(target).getConfig().id(), offset > 0);
    }

    public int indexOf(String id) {
        for (var index = 0; index < dockItems.size(); index++) {
            if (dockItems.get(index).getConfig().id().equals(id)) {
                return index;
            }
        }

        return -1;
    }

    void openTabMenu(DockItemNode tab) {
        if (onTabMenu != null && !tab.isDragging()) {
            onTabMenu.accept(tab);
        }
    }

    public void dispose() {
        viewport.getIndicator().dispose();
        cancelInteraction();
        prefWidthProperty().unbind();
        minWidthProperty().unbind();
        maxWidthProperty().unbind();
        dockItems.forEach(DockItemNode::dispose);
    }

    public void cancelInteraction() {
        dragManager.cancel();

        viewport.cancel();
    }

    @Getter
    private final AiDockDragManager dragManager;

    public AiDock(List<AiConfig> configs, FxWebViewPane fxWebViewPane, AppPreferences appPreferences) {
        this(configs, fxWebViewPane, appPreferences, null);
    }

    public AiDock(List<AiConfig> configs, FxWebViewPane fxWebViewPane, AppPreferences appPreferences, String activeId) {
        this.fxWebViewPane = fxWebViewPane;
        this.appPreferences = appPreferences;
        this.dragManager = new AiDockDragManager(this, appPreferences);
        DockDragScroll.install(this);

        var userIconsDir = new File(AppPaths.DATA_DIR, "icons");

        this.setAlignment(Pos.CENTER_LEFT);
        this.setMaxWidth(MAX_DOCK_WIDTH);
        this.setMinWidth(0);
        this.setMinHeight(DOCK_HEIGHT);
        this.setPrefHeight(DOCK_HEIGHT);
        this.setMaxHeight(DOCK_HEIGHT);
        this.setStyle("-fx-background-color: transparent;");

        viewport = new AiDockViewport(this);
        scrollPane = viewport.getScrollPane();
        dockContainer = viewport.getDockContainer();
        getChildren().add(viewport);

        var orderedConfigs = AiDockOrderUtils.applyCustomOrder(configs, appPreferences);
        var initial = InitialSiteSelection.resolve(orderedConfigs, appPreferences, activeId);

        for (var config : orderedConfigs) {
            AiDockIconUtils.preloadIcon(config, userIconsDir);

            var node = new DockItemNode(config, this);
            dockItems.add(node);
            dockContainer.getChildren().add(node);
        }

        refreshShortcutHints();
        refreshSound();

        if (!dockItems.isEmpty()) {
            selectItem(dockItems.get(initial.index()), initial.url());
        }
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

    /** Puts a dot on the tab of a site that changed while another site was open. */
    /** Shows a speaker on each site that plays sound, crossed out on those that are muted. */
    public void refreshSound() {
        dockItems.forEach(item -> {
            var id = item.getConfig().id();
            item.showSound(fxWebViewPane.isPlayingAudio(id), appPreferences.isSiteMuted(id, MuteRule.ALWAYS));
        });
    }

    public void markUpdated(String siteId) {
        dockItems.stream()
                .filter(item -> item != selectedNode && item.getConfig().id().equals(siteId))
                .forEach(item -> item.setUpdated(true));
    }

    /** Opens the site a keyboard shortcut points to, such as Ctrl + 2 or Ctrl + Tab. */
    public void select(SiteShortcut shortcut) {
        var target = shortcut.targetIndex(dockItems.indexOf(selectedNode), dockItems.size());

        if (target >= 0) {
            selectItem(dockItems.get(target));
        }
    }

    private void refreshShortcutHints() {
        for (var index = 0; index < dockItems.size(); index++) {
            dockItems.get(index).setShortcutHint(SiteShortcut.hintFor(index, dockItems.size()));
        }
    }

    /**
     * @param targetUrl page of the provider to open instead of its base URL, or {@code null}
     */
    private void selectItem(DockItemNode node, String targetUrl) {
        if (selectedNode == node) {
            return;
        }

        if (selectedNode != null) {
            selectedNode.setSelected(false);
        }

        selectedNode = node;
        selectedNode.setSelected(true);
        selectedNode.setUpdated(false);
        viewport.getIndicator().select(node);
        fxWebViewPane.setCurrentConfig(node.getConfig(), targetUrl);
        updateTopBarColor();
        revealSelection();

        if (onSiteSelected != null) {
            onSiteSelected.run();
        }
    }

    private void updateTopBarColor() {
        Platform.runLater(() -> {
            if (selectedNode == null) {
                return;
            }

            if (onAccentChanged != null) {
                onAccentChanged.accept(SiteAccent.resolve(selectedNode.getConfig().color()));
            }
        });
    }
}
