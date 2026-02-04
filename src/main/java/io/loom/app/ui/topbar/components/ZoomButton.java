package io.loom.app.ui.topbar.components;

import io.loom.app.ui.Theme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class ZoomButton extends JPanel {
    private String text = "";

    private float progress = 0f;
    private boolean hovered = false;
    private final Timer animTimer;

    public ZoomButton(Runnable action) {
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setVisible(false);

        animTimer = new Timer(10, e -> tick());

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                action.run();
            }

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
        });
    }

    public void setText(String text) {
        this.text = text;
        var fm = new Canvas().getFontMetrics(Theme.FONT_SELECTOR);
        var width = fm.stringWidth(text) + 10;
        setPreferredSize(new Dimension(width, 30));
        revalidate();
        repaint();
    }

    private void tick() {
        var target = hovered ? 1f : 0f;
        var diff = target - progress;
        if (Math.abs(diff) < 0.035f) {
            progress = target;
            animTimer.stop();
            Toolkit.getDefaultToolkit().sync();
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
        g.setFont(Theme.FONT_SELECTOR);

        var color = Theme.lerp(Theme.TEXT_SECONDARY, Theme.TEXT_PRIMARY, progress);
        g.setColor(color);

        var fm = g.getFontMetrics();
        var x = (getWidth() - fm.stringWidth(text)) / 2;
        var y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;

        g.drawString(text, x, y);

        if (progress > 0.1f) {
            g.setStroke(new BasicStroke(1f));
            var lineY = y + 4;
            var lineW = (int) (fm.stringWidth(text) * progress);
            var lineX = (getWidth() - lineW) / 2;
            g.drawLine(lineX, lineY, lineX + lineW, lineY);
        }
    }

    public void updateZoomDisplay(Double percent) {
        SwingUtilities.invokeLater(() -> {
            var val = Math.round(percent);
            if (val == 100) {
                this.setVisible(false);
            } else {
                this.setText(val + "%");
                this.setVisible(true);
            }

            var topBar = SwingUtilities.getAncestorOfClass(GradientPanel.class, this);
            if (topBar != null) {
                topBar.revalidate();
                topBar.repaint();
            }
        });
    }
}
