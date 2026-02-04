package io.loom.app.ui.settings.components;

import io.loom.app.ui.Theme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

public class ColorfulButton extends JPanel {

    private final String text;
    private final Color accentColor;
    private final Timer animTimer;
    private float hoverProgress = 0f;
    private boolean hovered = false;

    public ColorfulButton(String text, Color accentColor, Runnable action) {
        this.text = text;
        this.accentColor = accentColor;

        var isIconMode = text.length() <= 2;

        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setLayout(null);

        var font = new Font("Segoe UI Emoji", Font.PLAIN, 13);
        if (font.getFamily().equals("Dialog")) {
            font = Theme.FONT_SELECTOR;
        }
        setFont(font);

        Dimension dim;
        if (isIconMode) {
            dim = new Dimension(30, 30);
        } else {
            var fm = new Canvas().getFontMetrics(font);
            dim = new Dimension(fm.stringWidth(text) + 36, 34);
        }
        setPreferredSize(dim);
        setMaximumSize(dim);
        setMinimumSize(dim);

        animTimer = new Timer(16, e -> tick());

        addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                hovered = true;
                animTimer.start();
            }

            public void mouseExited(MouseEvent e) {
                hovered = false;
                animTimer.start();
            }

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
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        var baseBg = Theme.BG_POPUP;
        var mixedBg = new Color(
                (baseBg.getRed() + accentColor.getRed()) / 2,
                (baseBg.getGreen() + accentColor.getGreen()) / 2,
                (baseBg.getBlue() + accentColor.getBlue()) / 2
        );
        var hoverBg = Theme.lerp(baseBg, mixedBg, 0.2f);

        g.setColor(Theme.lerp(baseBg, hoverBg, hoverProgress));
        g.fill(new RoundRectangle2D.Float(0, 0, w - 1, h - 1, 10, 10));

        int alpha = 180 + (int) (75 * hoverProgress);
        g.setColor(new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), Math.min(255, alpha)));
        float strokeW = 1.0f + hoverProgress;
        g.setStroke(new BasicStroke(strokeW));
        g.draw(new RoundRectangle2D.Float(strokeW / 2, strokeW / 2, w - strokeW - 1, h - strokeW - 1, 10, 10));

        g.setFont(getFont());
        g.setColor(Theme.TEXT_PRIMARY);

        var fm = g.getFontMetrics();
        int textX = (w - fm.stringWidth(text)) / 2;
        int textY = (h + fm.getAscent() - fm.getDescent()) / 2 + 1;
        g.drawString(text, textX, textY);
    }
}