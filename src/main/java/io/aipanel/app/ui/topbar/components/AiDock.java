package io.aipanel.app.ui.topbar.components;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import io.aipanel.app.config.AiConfiguration;
import io.aipanel.app.config.AppPreferences;
import io.aipanel.app.ui.CefWebView;
import io.aipanel.app.ui.Theme;
import lombok.Getter;
import lombok.Setter;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AiDock extends JPanel {

    private static final int ICON_SIZE = 24;
    private static final int ITEM_HEIGHT = 32;

    private static final int PAD = 12;
    private static final int GAP = 8;
    private static final int ITEM_MARGIN = 6;

    private final List<DockItem> dockItems = new ArrayList<>();
    private final CefWebView cefWebView;
    private final Timer animationTimer;
    private final AppPreferences appPreferences;

    private static final Map<String, Image> ICON_CACHE = new ConcurrentHashMap<>();
    private int selectedIndex;

    public AiDock(List<AiConfiguration.AiConfig> configs, CefWebView cefWebView, AppPreferences appPreferences) {
        this.cefWebView = cefWebView;
        this.appPreferences = appPreferences;

        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // 1. Сначала определяем, какой индекс должен быть активным
        String lastUrl = appPreferences.getLastUrl();
        int initialIndex = 0; // По дефолту первый

        if (lastUrl != null) {
            for (int i = 0; i < configs.size(); i++) {
                if (configs.get(i).url().equals(lastUrl)) {
                    initialIndex = i;
                    break;
                }
            }
        }
        this.selectedIndex = initialIndex;

        // 2. Создаем элементы и сразу выставляем правильный флаг
        for (int i = 0; i < configs.size(); i++) {
            var item = new DockItem(configs.get(i), i);
            if (i == initialIndex) {
                item.setSelected(true);
            }
            dockItems.add(item);
            preloadIcon(configs.get(i));
        }

        // 3. Запускаем таймер и слушатели
        animationTimer = new Timer(15, e -> animate());
        setupMouseListeners();

        // 4. И только ТЕПЕРЬ рассчитываем ширину.
        // Так как selectedIndex уже верный, ширина рассчитается правильно сразу.
        calculateTargets(false);
        for (var item : dockItems) {
            item.currentWidth = item.targetWidth;
        }
        revalidateWidth();

        // 5. Бонус: Обновляем цвет шапки при старте (чтобы не был дефолтным, если выбрана другая ИИ)
        // Делаем это через invokeLater, чтобы UI успел собраться
        SwingUtilities.invokeLater(() -> {
            var topBarComp = SwingUtilities.getAncestorOfClass(GradientPanel.class, this);
            if (topBarComp instanceof GradientPanel topBar) {
                var cfg = configs.get(selectedIndex);
                if (cfg.color() != null) {
                    try {
                        topBar.setAccentColor(Color.decode(cfg.color()));
                        topBar.repaint();
                    } catch (Exception ignored) {}
                }
            }
        });
    }

    private void setupMouseListeners() {
        var ma = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                animationTimer.start();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                for (var item : dockItems) {
                    item.setHovered(false);
                }
                animationTimer.start();
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                handleMouseMove(e.getX());
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                handleMouseClick(e.getX());
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
    }

    private void calculateTargets(boolean isDockHovered) {
        for (var item : dockItems) {
            float targetW;

            if (item.isSelected()) {
                // Selected: Full width
                targetW = PAD + ICON_SIZE + GAP + getTextWidth(item.config.name()) + PAD;
            } else {
                if (isDockHovered) {
                    if (item.isHovered()) {
                        // Hovered: Full width
                        targetW = PAD + ICON_SIZE + GAP + getTextWidth(item.config.name()) + PAD;
                    } else {
                        // Visible but not hovered: Icon only
                        targetW = PAD + ICON_SIZE + PAD;
                    }
                } else {
                    // Dock not hovered: Hidden
                    targetW = 0f;
                }
            }
            item.setTargetWidth(targetW);
        }
    }

    private void animate() {
        var needsRepaint = false;
        var allDone = true;

        var mousePt = getMousePosition();
        var isDockHovered = (mousePt != null);

        calculateTargets(isDockHovered);

        for (var item : dockItems) {
            float diff = item.targetWidth - item.currentWidth;
            if (Math.abs(diff) > 0.5f) {
                item.currentWidth += diff * 0.2f; // Interpolation speed
                needsRepaint = true;
                allDone = false;
            } else {
                item.currentWidth = item.targetWidth;
            }
        }

        if (needsRepaint) {
            revalidateWidth();
            // Ищем родителя (TopBarPanel) и просим его перерисоваться
            var topBar = SwingUtilities.getAncestorOfClass(GradientPanel.class, this);
            if (topBar != null) topBar.repaint(); else repaint();
        } else if (allDone && !isDockHovered) {
            animationTimer.stop();
        }
    }

    private void revalidateWidth() {
        var totalW = 0;
        for (var item : dockItems) {
            totalW += (int) item.currentWidth;
            if (item.currentWidth > 1) totalW += ITEM_MARGIN;
        }
        setPreferredSize(new Dimension(totalW, 48));
        revalidate();
    }

    private void handleMouseMove(int x) {
        float currentX = 0;
        var foundHover = false;

        // 1. Check Selected Item (Always first)
        var selected = dockItems.get(selectedIndex);
        if (x >= currentX && x <= currentX + selected.currentWidth) {
            updateHoverState(selected);
            foundHover = true;
        }
        currentX += selected.currentWidth + ITEM_MARGIN;

        // 2. Check Others
        if (!foundHover) {
            for (int i = 0; i < dockItems.size(); i++) {
                if (i == selectedIndex) continue;

                var item = dockItems.get(i);
                if (item.currentWidth < 1) continue;

                if (x >= currentX && x <= currentX + item.currentWidth) {
                    updateHoverState(item);
                    foundHover = true;
                    break;
                }
                currentX += item.currentWidth + ITEM_MARGIN;
            }
        }

        if (!foundHover) {
            updateHoverState(null);
        }
    }

    private void updateHoverState(DockItem target) {
        var changed = false;
        for (var item : dockItems) {
            var shouldBeHovered = (item == target);
            if (item.isHovered() != shouldBeHovered) {
                item.setHovered(shouldBeHovered);
                changed = true;
            }
        }
        if (changed) animationTimer.start();
    }

    private void handleMouseClick(int x) {
        float currentX = 0;

        var selectedItem = dockItems.get(selectedIndex);
        if (x >= currentX && x <= currentX + selectedItem.currentWidth) {
            return; // Clicked on already selected
        }
        currentX += selectedItem.currentWidth + ITEM_MARGIN;

        for (int i = 0; i < dockItems.size(); i++) {
            if (i == selectedIndex) continue;
            var item = dockItems.get(i);
            if (item.currentWidth < 1) continue;

            if (x >= currentX && x <= currentX + item.currentWidth) {
                selectItem(i);
                return;
            }
            currentX += item.currentWidth + ITEM_MARGIN;
        }
    }

    private void selectItem(int index) {
        if (index == selectedIndex) return;

        dockItems.get(selectedIndex).setSelected(false);

        selectedIndex = index;
        var newItem = dockItems.get(index);
        newItem.setSelected(true);

        cefWebView.loadUrl(newItem.config.url());
        if (appPreferences != null) {
            appPreferences.setLastUrl(newItem.config.url());
        }
        var topBarComp = SwingUtilities.getAncestorOfClass(GradientPanel.class, this);
        if (topBarComp instanceof GradientPanel topBar) {
            Color accent = Theme.ACCENT;
            if (newItem.config.color() != null) {
                try {
                    accent = Color.decode(newItem.config.color());
                } catch (NumberFormatException ignored) {
                    // Color will be Theme.ACCENT if incorrect format
                }
            }
            topBar.setAccentColor(accent);
            topBar.repaint();
        }

        animationTimer.start();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        var g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int y = (getHeight() - ITEM_HEIGHT) / 2;
        float currentX = 0;

        // 1. Draw Selected
        var selectedItem = dockItems.get(selectedIndex);
        drawDockItem(g, selectedItem, (int) currentX, y);
        currentX += selectedItem.currentWidth + ITEM_MARGIN;

        // 2. Draw Others
        for (int i = 0; i < dockItems.size(); i++) {
            if (i == selectedIndex) continue;
            var item = dockItems.get(i);
            if (item.currentWidth > 1.0f) {
                drawDockItem(g, item, (int) currentX, y);
                currentX += item.currentWidth + ITEM_MARGIN;
            }
        }
    }

    private void drawDockItem(Graphics2D g, DockItem item, int x, int y) {
        int w = (int) item.currentWidth;
        if (w <= 0) return;

        Shape shape = new RoundRectangle2D.Float(x, y, w, ITEM_HEIGHT, ITEM_HEIGHT, ITEM_HEIGHT);

        // --- Background ---
        if (item.isSelected()) {
            var bg = Theme.ACCENT;
            try {
                if (item.config.color() != null) bg = Color.decode(item.config.color());
            } catch (Exception ignored) {
            }
            g.setColor(bg);
            g.fill(shape);
        } else {
            g.setColor(item.isHovered() ? Theme.BG_HOVER : Theme.BG_POPUP);
            g.fill(shape);

            g.setColor(Theme.BORDER);
            g.setStroke(new BasicStroke(1f));
            g.draw(shape);
        }

        // --- Icon ---
        var icon = ICON_CACHE.get(item.config.icon());
        if (icon != null) {
            int iconY = y + (ITEM_HEIGHT - ICON_SIZE) / 2;
            g.drawImage(icon, x + PAD, iconY, ICON_SIZE, ICON_SIZE, null);
        }

        // --- Text ---
        // Only draw text if the width is strictly larger than the collapsed state (Icon + 2*Pad).
        // This prevents 1-2px bleed when width is exactly PAD+ICON+PAD.
        if (w > PAD + ICON_SIZE + PAD) {
            g.setFont(Theme.FONT_SELECTOR);
            g.setColor(Theme.TEXT_PRIMARY);

            var fm = g.getFontMetrics();
            int textX = x + PAD + ICON_SIZE + GAP;
            int textY = y + (ITEM_HEIGHT + fm.getAscent() - fm.getDescent()) / 2;

            var oldClip = g.getClip();
            g.setClip(shape);
            g.drawString(item.config.name(), textX, textY);
            g.setClip(oldClip);
        }
    }

    private int getTextWidth(String text) {
        return new Canvas().getFontMetrics(Theme.FONT_SELECTOR).stringWidth(text);
    }

    private void preloadIcon(AiConfiguration.AiConfig cfg) {
        if (cfg.icon() == null) {
            return;
        }

        ICON_CACHE.computeIfAbsent(cfg.icon(), key -> {
            try {
                var url = AiDock.class.getResource("/icons/" + key);
                if (url == null) {
                    return null;
                }

                if (key.toLowerCase().endsWith(".svg")) {
                    var icon = new FlatSVGIcon(url);
                    var scaledIcon = icon.derive(ICON_SIZE, ICON_SIZE);
                    var img = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
                    var g = img.createGraphics();

                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                    scaledIcon.paintIcon(null, g, 0, 0);
                    g.dispose();

                    return img;
                }

                else {
                    var original = ImageIO.read(url);
                    return resize(original, ICON_SIZE, ICON_SIZE);
                }
            } catch (Exception e) {
                 e.printStackTrace(); // fixme add logs
            }
            return null;
        });
    }

    private Image resize(BufferedImage img, int w, int h) {
        var resized = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        var g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, 0, 0, w, h, null);
        g.dispose();
        return resized;
    }

    @Getter
    @Setter
    private static class DockItem {
        private final AiConfiguration.AiConfig config;
        private final int index;

        private float currentWidth = 0f;
        private float targetWidth = 0f;

        private boolean isSelected = false;
        private boolean isHovered = false;

        public DockItem(AiConfiguration.AiConfig config, int index) {
            this.config = config;
            this.index = index;
        }
    }
}