package io.aipanel.app.ui.topbar.components;

import io.aipanel.app.ui.Theme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;

public class AnimatedIconButton extends JPanel {

    private static final int SIZE = 30;
    private static final int RING_R = 13;
    private static final int INTERVAL = 16;

    private final String icon;
    private final Color hoverColor;
    private final Runnable action;

    private float progress = 0f;
    private boolean hovered = false;
    private final Timer animTimer;

    public AnimatedIconButton(String icon, Color hoverColor, Runnable action) {
        this.icon = icon;
        this.hoverColor = hoverColor;
        this.action = action;

        setPreferredSize(new Dimension(SIZE, SIZE));
        setMinimumSize(new Dimension(SIZE, SIZE));
        setMaximumSize(new Dimension(SIZE, SIZE));
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        animTimer = new Timer(INTERVAL, e -> tick());

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hovered = true;
                animTimer.start();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hovered = false;
                animTimer.start();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                action.run();
            }
        });
    }

    private void tick() {
        float target = hovered ? 1f : 0f;
        float diff = target - progress;

        if (Math.abs(diff) < 0.035f) {
            progress = target;
            animTimer.stop();
        } else {
            progress += diff * 0.22f;
        }

        var topBar = SwingUtilities.getAncestorOfClass(GradientPanel.class, this);
        if (topBar != null) {
            topBar.repaint();
        } else {
            repaint();
        }
    }

    @Override
    protected void paintComponent(Graphics g0) {
        var g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int cx = getWidth() / 2;
        int cy = getHeight() / 2;

        if (progress > 0.02f) {
            float alpha = progress * 0.6f;
            g.setColor(Theme.withAlpha(Theme.BTN_RING, alpha));
            g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Ellipse2D.Float(cx - RING_R, cy - RING_R, RING_R * 2, RING_R * 2));
        }

        g.setColor(Theme.lerp(Theme.TEXT_SECONDARY, hoverColor, progress));
        g.setFont(Theme.FONT_RIGHT_TOP_BAR_AREA);

        var fm = g.getFontMetrics();
        int textX = cx - fm.stringWidth(icon) / 2;
        int textY = cy + (fm.getAscent() - fm.getDescent()) / 2;
        g.drawString(icon, textX, textY);
    }
}
