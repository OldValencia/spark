package io.aipanel.app.ui.topbar.components;

import io.aipanel.app.ui.Theme;
import lombok.Setter;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

public class GradientPanel extends JPanel {

    @Setter
    private Color accentColor = Theme.ACCENT;

    public GradientPanel() {
        setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        var g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();

        // Cleanup
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC));
        g.setColor(new Color(0, 0, 0, 0));
        g.fillRect(0, 0, w, h);

        // Background
        g.setColor(Theme.BG_BAR);
        var shape = new RoundRectangle2D.Float(0, 0, w, h, 14, 14);
        g.fill(shape);

        // Gradients
        g.setComposite(AlphaComposite.SrcOver);
        var oldClip = g.getClip();
        g.setClip(shape);

        var transparent = new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), 0);
        var faded = new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), 40);
        g.setPaint(new LinearGradientPaint(
                new Point(0, 0), new Point(50, 0),
                new float[]{0f, 1f},
                new Color[]{transparent, faded}
        ));
        g.fillRect(0, 0, 50, h);

        int fadeWidth = (int) (w * 0.20);
        g.setPaint(new LinearGradientPaint(
                new Point(50, 0), new Point(50 + fadeWidth, 0),
                new float[]{0f, 1f},
                new Color[]{faded, transparent}
        ));
        g.fillRect(50, 0, fadeWidth, h);

        g.setClip(oldClip);

        // Stroke
        g.setColor(Theme.BORDER);
        g.setStroke(new BasicStroke(1f));
        g.drawLine(0, h - 1, w, h - 1);
    }

    public void updateAccentColor(Color c) {
        this.setAccentColor(c);
        this.repaint();
    }
}
