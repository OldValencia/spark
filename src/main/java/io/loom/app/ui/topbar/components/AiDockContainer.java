package io.loom.app.ui.topbar.components;

import io.loom.app.ui.Theme;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

@Slf4j
class AiDockContainer extends JPanel {

    private final List<AiDock.DockItem> dockItems;

    AiDockContainer(List<AiDock.DockItem> dockItems) {
        super();

        this.dockItems = dockItems;

        this.setOpaque(false);
        this.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    @Override
    protected void paintComponent(Graphics g0) {
        var g = (Graphics2D) g0;
        Shape oldClip = g.getClip();
        Rectangle visibleRect = getVisibleRect();
        g.clipRect(visibleRect.x, visibleRect.y, visibleRect.width, visibleRect.height);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        try {
            int y = (getHeight() - AiDock.ITEM_HEIGHT) / 2;
            float currentX = 0;

            for (var item : dockItems) {
                if (item.getCurrentWidth() > 1.0f) {
                    drawDockItem(g, item, (int) currentX, y);
                    currentX += item.getCurrentWidth() + AiDock.ITEM_MARGIN;
                }
            }
        } finally {
            g.setClip(oldClip);
        }
    }

    private void drawDockItem(Graphics2D g, AiDock.DockItem item, int x, int y) {
        var w = (int) item.getCurrentWidth();
        if (w <= 0) {
            return;
        }

        var shape = new RoundRectangle2D.Float(x, y, w, AiDock.ITEM_HEIGHT, AiDock.ITEM_HEIGHT, AiDock.ITEM_HEIGHT);

        if (item.isSelected()) {
            var bg = Theme.ACCENT;
            try {
                if (item.getConfig().color() != null) {
                    bg = Color.decode(item.getConfig().color());
                }
            } catch (Exception e) {
                log.warn("Warning while trying to draw dock item ({})", item.getConfig().name(), e);
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

        Image icon = null;
        if (item.getConfig().icon() != null) {
            icon = AiDock.ICON_CACHE.get(item.getConfig().icon());
        }

        if (icon != null) {
            var iconY = y + (AiDock.ITEM_HEIGHT - AiDock.ICON_SIZE) / 2;
            g.drawImage(icon, x + AiDock.PAD, iconY, AiDock.ICON_SIZE, AiDock.ICON_SIZE, null);
        }

        if (w > AiDock.PAD + AiDock.ICON_SIZE + AiDock.PAD) {
            g.setFont(Theme.FONT_SELECTOR);
            g.setColor(Theme.TEXT_PRIMARY);
            var fm = g.getFontMetrics();
            var textX = x + AiDock.PAD + AiDock.ICON_SIZE + AiDock.GAP;
            var textY = y + (AiDock.ITEM_HEIGHT + fm.getAscent() - fm.getDescent()) / 2;
            var oldClip = g.getClip();
            g.setClip(shape);
            g.drawString(item.getConfig().name(), textX, textY);
            g.setClip(oldClip);
        }
    }
}
