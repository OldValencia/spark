package io.loom.app.ui.topbar.components;

import io.loom.app.config.AiConfiguration;
import io.loom.app.config.AppPreferences;
import io.loom.app.ui.FxWebViewPane;
import io.loom.app.ui.Theme;
import io.loom.app.ui.topbar.utils.AiDockIconUtils;
import io.loom.app.ui.topbar.utils.AiDockOrderUtils;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class AiDock extends ScrollPane {

    public static final Map<String, javafx.scene.image.Image> ICON_CACHE = new ConcurrentHashMap<>();
    public static final int ICON_SIZE = 24;

    static final int ITEM_HEIGHT = 32;
    static final int ITEM_MARGIN = 6;
    static final int PAD = 12;
    static final int GAP = 8;

    private static final int MAX_DOCK_WIDTH = 500;

    private final HBox dockContainer;
    private final List<DockItemNode> dockItems = new ArrayList<>();
    private final FxWebViewPane fxWebViewPane;
    private final AppPreferences appPreferences;

    private DockItemNode selectedNode = null;

    public AiDock(List<AiConfiguration.AiConfig> configs, FxWebViewPane fxWebViewPane, AppPreferences appPreferences) {
        this.fxWebViewPane = fxWebViewPane;
        this.appPreferences = appPreferences;

        var userIconsDir = new File(AppPreferences.DATA_DIR, "icons");

        this.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        this.setHbarPolicy(ScrollBarPolicy.AS_NEEDED);
        this.setVbarPolicy(ScrollBarPolicy.NEVER);
        this.setFitToHeight(true);
        this.setMaxWidth(MAX_DOCK_WIDTH);

        dockContainer = new HBox();
        dockContainer.setSpacing(ITEM_MARGIN);
        dockContainer.setStyle("-fx-background-color: transparent;");
        dockContainer.setPadding(new Insets(8, 0, 8, 0));
        this.setContent(dockContainer);

        var orderedConfigs = AiDockOrderUtils.applyCustomOrder(configs, appPreferences);
        var lastUrl = appPreferences.getLastUrl();
        var initialIndex = 0;

        if (lastUrl != null) {
            for (int i = 0; i < orderedConfigs.size(); i++) {
                if (orderedConfigs.get(i).url().equals(lastUrl)) {
                    initialIndex = i;
                    break;
                }
            }
        }

        for (AiConfiguration.AiConfig config : orderedConfigs) {
            AiDockIconUtils.preloadIcon(config, userIconsDir);
            var node = new DockItemNode(config, this);
            dockItems.add(node);
            dockContainer.getChildren().add(node);
        }

        if (!dockItems.isEmpty()) {
            selectItem(dockItems.get(initialIndex));
        }

        setupDockMouseListeners();
    }

    public static void clearIconCache() {
        ICON_CACHE.clear();
        log.debug("Icon cache cleared");
    }

    public static void pruneIconCache(List<String> activeIcons) {
        ICON_CACHE.keySet().retainAll(activeIcons);
        log.info("Icon cache pruned, {} icons remaining", ICON_CACHE.size());
    }

    private void setupDockMouseListeners() {
        dockContainer.setOnMouseEntered(e -> {
            for (var item : dockItems) {
                item.setDockHovered(true);
            }
        });

        dockContainer.setOnMouseExited(e -> {
            for (var item : dockItems) {
                item.setDockHovered(false);
            }
        });
    }

    public void selectItem(DockItemNode node) {
        if (selectedNode == node) {
            return;
        }

        if (selectedNode != null) {
            selectedNode.setSelected(false);
        }

        selectedNode = node;
        selectedNode.setSelected(true);

        fxWebViewPane.setCurrentConfig(node.getConfig());

        if (appPreferences != null) {
            appPreferences.setLastUrl(node.getConfig().url());
        }

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
                if (child instanceof DockItemNode) {
                    dockItems.add((DockItemNode) child);
                }
            }

            // AppPreferences is saved immediately after dropping
            AiDockOrderUtils.saveCurrentOrder(dockItems, appPreferences);
        }
    }

    private void updateTopBarColor() {
        Platform.runLater(() -> {
            var parent = this.getParent();

            // Traverse up to find GradientPanel
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
