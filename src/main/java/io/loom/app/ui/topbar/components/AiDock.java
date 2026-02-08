package io.loom.app.ui.topbar.components;

import io.loom.app.config.AiConfiguration;
import io.loom.app.config.AppPreferences;
import io.loom.app.ui.CefWebView;
import io.loom.app.ui.Theme;
import io.loom.app.ui.topbar.utils.AiDockIconUtils;
import io.loom.app.ui.topbar.utils.AiDockOrderUtils;
import io.loom.app.utils.SystemUtils;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class AiDock extends JPanel {

    public static final Map<String, Image> ICON_CACHE = new ConcurrentHashMap<>();
    public static final int ICON_SIZE = 24;

    static final int ITEM_HEIGHT = 32;
    static final int ITEM_MARGIN = 6;
    static final int PAD = 12;
    static final int GAP = 8;

    private static final int DRAG_THRESHOLD = 10;
    private static final int MAX_DOCK_WIDTH = 500;

    private final List<DockItem> dockItems = new ArrayList<>();
    private final CefWebView cefWebView;
    private final AppPreferences appPreferences;
    private final Timer animationTimer;

    private final JPanel dockContainer;

    private int selectedIndex = 0;
    private boolean isDragging = false;
    private int dragStartIndex = -1;
    private int pressX = -1;
    private int pressY = -1;
    private boolean isMouseInside = false;

    public AiDock(List<AiConfiguration.AiConfig> configs, CefWebView cefWebView, AppPreferences appPreferences) {
        this.cefWebView = cefWebView;
        this.appPreferences = appPreferences;

        var userIconsDir = new File(AppPreferences.DATA_DIR, "icons");

        setOpaque(false);
        setLayout(new BorderLayout());

        dockContainer = new AiDockContainer(dockItems);
        add(new AiDockScrollPane(dockContainer), BorderLayout.CENTER);

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
        this.selectedIndex = initialIndex;

        for (int i = 0; i < orderedConfigs.size(); i++) {
            var item = new DockItem(orderedConfigs.get(i), i);
            if (i == initialIndex) {
                item.setSelected(true);
            }
            dockItems.add(item);
            AiDockIconUtils.preloadIcon(orderedConfigs.get(i), userIconsDir);
        }

        int timerDelay = SystemUtils.isMac() ? 16 : 10;
        animationTimer = new Timer(timerDelay, e -> animate());
        animationTimer.setCoalesce(true);

        setupMouseListeners();

        calculateTargets(false);
        for (var item : dockItems) {
            item.currentWidth = item.targetWidth;
        }
        revalidateWidth();

        SwingUtilities.invokeLater(() -> {
            updateTopBarColor();
            if (!dockItems.isEmpty()) {
                cefWebView.setCurrentConfig(dockItems.get(selectedIndex).config);
            }
        });
    }

    public static void clearIconCache() {
        ICON_CACHE.clear();
        log.info("Icon cache cleared");
    }

    public static void pruneIconCache(List<String> activeIcons) {
        ICON_CACHE.keySet().retainAll(activeIcons);
        log.info("Icon cache pruned, {} icons remaining", ICON_CACHE.size());
    }

    private void setupMouseListeners() {
        var mouseAdapter = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                isMouseInside = true;
                if (!isDragging && !animationTimer.isRunning()) {
                    animationTimer.start();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                isMouseInside = false;
                if (!isDragging) {
                    Point mousePos = e.getPoint();
                    if (!dockContainer.contains(mousePos)) {
                        for (var item : dockItems) {
                            item.setHovered(false);
                        }
                        if (!animationTimer.isRunning()) {
                            animationTimer.start();
                        }
                    }
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    pressX = e.getX();
                    pressY = e.getY();
                    dragStartIndex = getItemIndexAt(pressX);
                    isDragging = false;
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }

                if (!isDragging) {
                    if (Math.abs(e.getX() - pressX) > DRAG_THRESHOLD || Math.abs(e.getY() - pressY) > DRAG_THRESHOLD) {
                        isDragging = true;
                        if (!animationTimer.isRunning()) {
                            animationTimer.start();
                        }
                    }
                }

                if (isDragging && dragStartIndex != -1) {
                    handleDrag(e.getX());
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }

                if (isDragging) {
                    isDragging = false;
                    dragStartIndex = -1;
                    AiDockOrderUtils.saveCurrentOrder(dockItems, appPreferences);
                    if (!animationTimer.isRunning()) {
                        animationTimer.start();
                    }
                } else {
                    int index = getItemIndexAt(e.getX());
                    if (index != -1) {
                        selectItem(index);
                    }
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                if (!isDragging && isMouseInside) {
                    handleMouseMove(e.getX());
                }
            }
        };

        dockContainer.addMouseListener(mouseAdapter);
        dockContainer.addMouseMotionListener(mouseAdapter);
    }

    private int getItemIndexAt(int x) {
        float currentX = 0;

        for (int i = 0; i < dockItems.size(); i++) {
            DockItem item = dockItems.get(i);

            if (item.currentWidth < 1) {
                continue;
            }

            if (x >= currentX && x <= currentX + item.currentWidth) {
                return i;
            }
            currentX += item.currentWidth + ITEM_MARGIN;
        }
        return -1;
    }

    private void handleDrag(int x) {
        var targetIndex = getItemIndexAt(x);
        if (targetIndex != -1 && targetIndex != dragStartIndex) {
            Collections.swap(dockItems, dragStartIndex, targetIndex);

            if (selectedIndex == dragStartIndex) {
                selectedIndex = targetIndex;
            } else if (selectedIndex == targetIndex) {
                selectedIndex = dragStartIndex;
            }

            dragStartIndex = targetIndex;

            calculateTargets(true);
            revalidateWidth();
            repaintParents();
        }
    }

    private void calculateTargets(boolean isDockHovered) {
        for (var item : dockItems) {
            float targetW;
            var isItemHovered = item.isHovered() || (isDragging && dockItems.indexOf(item) == dragStartIndex);

            if (item.isSelected()) {
                targetW = PAD + ICON_SIZE + GAP + getTextWidth(item.config.name()) + PAD;
            } else {
                if (isDockHovered || isDragging || isMouseInside) {
                    if (isItemHovered) {
                        targetW = PAD + ICON_SIZE + GAP + getTextWidth(item.config.name()) + PAD;
                    } else {
                        targetW = PAD + ICON_SIZE + PAD;
                    }
                } else {
                    targetW = 0f;
                }
            }
            item.setTargetWidth(targetW);
        }
    }

    private void animate() {
        var needsRepaint = false;
        var isDockHovered = isMouseInside || isDragging;

        calculateTargets(isDockHovered);

        for (var item : dockItems) {
            var diff = item.targetWidth - item.currentWidth;
            if (Math.abs(diff) > 0.5f) {
                item.currentWidth += diff * 0.25f;
                needsRepaint = true;
            } else {
                item.currentWidth = item.targetWidth;
            }
        }

        if (needsRepaint) {
            revalidateWidth();
            dockContainer.repaint();

            if (SystemUtils.isMac()) {
                Toolkit.getDefaultToolkit().sync();
            }

            var topBar = SwingUtilities.getAncestorOfClass(GradientPanel.class, this);
            if (topBar != null) {
                topBar.repaint();
                Toolkit.getDefaultToolkit().sync();
            }

        } else if (!isDockHovered && !isMouseInside) {
            animationTimer.stop();
        }
    }

    private void revalidateWidth() {
        var totalW = 0;
        for (var item : dockItems) {
            totalW += (int) item.currentWidth;
            if (item.currentWidth > 1) {
                totalW += ITEM_MARGIN;
            }
        }

        int finalWidth = Math.min(totalW, MAX_DOCK_WIDTH);
        dockContainer.setPreferredSize(new Dimension(totalW, 48));
        setPreferredSize(new Dimension(finalWidth, 48));
        setMaximumSize(new Dimension(MAX_DOCK_WIDTH, 48));

        dockContainer.revalidate();
        revalidate();
    }

    private void repaintParents() {
        var topBar = SwingUtilities.getAncestorOfClass(GradientPanel.class, this);
        if (topBar != null) {
            topBar.repaint();
        }
    }

    private void handleMouseMove(int x) {
        var hoverIndex = getItemIndexAt(x);
        var changed = false;
        for (int i = 0; i < dockItems.size(); i++) {
            var shouldHover = (i == hoverIndex);
            if (dockItems.get(i).isHovered() != shouldHover) {
                dockItems.get(i).setHovered(shouldHover);
                changed = true;
            }
        }
        if (changed && !animationTimer.isRunning()) {
            animationTimer.start();
        }
    }

    private void selectItem(int index) {
        if (index == selectedIndex) {
            return;
        }

        dockItems.get(selectedIndex).setSelected(false);
        selectedIndex = index;
        var newItem = dockItems.get(index);
        newItem.setSelected(true);

        cefWebView.setCurrentConfig(newItem.config);

        if (appPreferences != null) {
            appPreferences.setLastUrl(newItem.config.url());
        }

        updateTopBarColor();
        if (!animationTimer.isRunning()) {
            animationTimer.start();
        }
    }

    private void updateTopBarColor() {
        var topBarComp = SwingUtilities.getAncestorOfClass(GradientPanel.class, this);
        if (topBarComp instanceof GradientPanel topBar) {
            var accent = Theme.ACCENT;
            try {
                if (dockItems.get(selectedIndex).config.color() != null) {
                    accent = Color.decode(dockItems.get(selectedIndex).config.color());
                }
            } catch (Exception e) {
                log.warn("Warning while trying to update topBarColor", e);
            }
            topBar.setAccentColor(accent);
        }
    }

    private int getTextWidth(String text) {
        return getFontMetrics(Theme.FONT_SELECTOR).stringWidth(text);
    }

    @Getter
    @Setter
    public static class DockItem {
        private final AiConfiguration.AiConfig config;
        @Getter(AccessLevel.PACKAGE)
        private float currentWidth = 0f;
        private final int originalIndex;
        private float targetWidth = 0f;
        private boolean isSelected = false;
        private boolean isHovered = false;

        private DockItem(AiConfiguration.AiConfig config, int index) {
            this.config = config;
            this.originalIndex = index;
        }
    }
}
