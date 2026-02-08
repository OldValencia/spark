package io.loom.app.ui.topbar.components;

import io.loom.app.ui.Theme;
import io.loom.app.utils.SystemUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

public class GradientPanel extends JPanel {

    private Color accentColor = Theme.ACCENT;
    private LinearGradientPaint cachedGradientLeft;
    private LinearGradientPaint cachedGradientFade;
    private RoundRectangle2D.Float cachedShape;
    private int lastWidth = -1;
    private int lastHeight = -1;

    private BufferedImage cachedBackground;
    private boolean needsRepaint = true;

    public GradientPanel() {
        setOpaque(false);
        rebuildGradients();

        if (SystemUtils.isWindows()) {
            setDoubleBuffered(true);
        }
    }

    public void setAccentColor(Color c) {
        if (this.accentColor.equals(c)) {
            return;
        }
        this.accentColor = c;
        rebuildGradients();
        needsRepaint = true;

        if (SystemUtils.isWindows()) {
            cachedBackground = null;
        }

        repaint();
    }

    public void updateAccentColor(Color c) {
        setAccentColor(c);
    }

    private void rebuildGradients() {
        var cachedTransparent = new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), 0);
        var cachedFaded = new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), 40);

        cachedGradientLeft = new LinearGradientPaint(
                new Point(0, 0), new Point(50, 0),
                new float[]{0f, 1f},
                new Color[]{cachedTransparent, cachedFaded}
        );

        var fadeWidth = (int) (820 * 0.20);
        cachedGradientFade = new LinearGradientPaint(
                new Point(50, 0), new Point(50 + fadeWidth, 0),
                new float[]{0f, 1f},
                new Color[]{cachedFaded, cachedTransparent}
        );
    }

    @Override
    protected void paintComponent(Graphics g0) {
        var w = getWidth();
        var h = getHeight();

        if (SystemUtils.isWindows()) {
            if (cachedBackground == null || lastWidth != w || lastHeight != h || needsRepaint) {
                cachedBackground = new BufferedImage(
                        Math.max(1, w),
                        Math.max(1, h),
                        BufferedImage.TYPE_INT_ARGB
                );
                Graphics2D cacheG = cachedBackground.createGraphics();
                cacheG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                paintToGraphics(cacheG, w, h);

                cacheG.dispose();
                lastWidth = w;
                lastHeight = h;
                needsRepaint = false;
            }

            var g = (Graphics2D) g0;
            g.setComposite(AlphaComposite.Clear);
            g.fillRect(0, 0, w, h);
            g.setComposite(AlphaComposite.SrcOver);
            g.drawImage(cachedBackground, 0, 0, null);
        } else {
            var g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            paintToGraphics(g, w, h);
            lastWidth = w;
            lastHeight = h;
        }
    }

    private void paintToGraphics(Graphics2D g, int w, int h) {
        if (cachedShape == null || lastWidth != w || lastHeight != h) {
            cachedShape = new RoundRectangle2D.Float(0, 0, w, h, 14, 14);
        }

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC));
        g.setColor(new Color(0, 0, 0, 0));
        g.fillRect(0, 0, w, h);

        g.setColor(Theme.BG_BAR);
        g.fill(cachedShape);

        g.setComposite(AlphaComposite.SrcOver);
        var oldClip = g.getClip();
        g.setClip(cachedShape);

        g.setPaint(cachedGradientLeft);
        g.fillRect(0, 0, 50, h);

        var fadeWidth = (int) (w * 0.20);
        g.setPaint(cachedGradientFade);
        g.fillRect(50, 0, fadeWidth, h);

        g.setClip(oldClip);

        g.setColor(Theme.BORDER);
        g.setStroke(new BasicStroke(1f));
        g.drawLine(0, h - 1, w, h - 1);
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
    }
}
