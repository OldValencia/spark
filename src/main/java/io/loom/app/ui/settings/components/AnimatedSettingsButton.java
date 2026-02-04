package io.loom.app.ui.settings.components;

import io.loom.app.ui.Theme;
import lombok.Setter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

public class AnimatedSettingsButton extends JPanel {

    private String text;
    private final Timer animTimer;

    private float hoverProgress = 0f;
    private boolean hovered = false;

    public void setText(String text) {
        this.text = text;
        this.tick();
    }

    public AnimatedSettingsButton(String text, Runnable action) {
        this.text = text;

        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setLayout(null);

        var fontMetrics = new Canvas().getFontMetrics(Theme.FONT_SELECTOR);
        setPreferredSize(new Dimension(fontMetrics.stringWidth(text) + 28, 30));

        animTimer = new Timer(16, e -> tick());

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
        var target = hovered ? 1f : 0f;
        var diff = target - hoverProgress;
        if (Math.abs(diff) < 0.04f) {
            hoverProgress = target;
            animTimer.stop();
        } else {
            hoverProgress += diff * 0.22f;
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        var g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        var bg = Theme.lerp(Theme.BG_POPUP, Theme.BG_HOVER, hoverProgress);
        g.setColor(bg);
        g.fill(new RoundRectangle2D.Float(0, 0, w, h, 8, 8));

        g.setColor(Theme.BORDER);
        g.setStroke(new BasicStroke(1f));
        g.draw(new RoundRectangle2D.Float(0, 0, w, h, 8, 8));

        g.setFont(Theme.FONT_SELECTOR);
        g.setColor(Theme.TEXT_PRIMARY);
        var fm = g.getFontMetrics();
        int textX = (w - fm.stringWidth(text)) / 2;
        int textY = (h + fm.getAscent() - fm.getDescent()) / 2;
        g.drawString(text, textX, textY);
    }
}
