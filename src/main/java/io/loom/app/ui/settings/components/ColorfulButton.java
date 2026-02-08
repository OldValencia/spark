package io.loom.app.ui.settings.components;

import io.loom.app.ui.Theme;
import io.loom.app.utils.SystemUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextLayout;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

class ColorfulButton extends JPanel {

    private final String text;
    private final Color accentColor;
    private final Timer animTimer;
    private float hoverProgress = 0f;
    private boolean hovered = false;

    ColorfulButton(String text, Color accentColor, Runnable action) {
        this.text = text;
        this.accentColor = accentColor;

        var isIconMode = text.length() <= 2;

        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setLayout(null);

        Font displayFont = getOptimalFont(text, isIconMode);
        setFont(displayFont);

        var tempImg = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        var tempG = tempImg.createGraphics();
        tempG.setFont(displayFont);
        tempG.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        tempG.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

        var frc = tempG.getFontRenderContext();
        TextLayout textLayout = new TextLayout(text, displayFont, frc);

        int textWidth = (int) Math.ceil(textLayout.getBounds().getWidth());
        tempG.dispose();

        Dimension dim;
        if (isIconMode) {
            dim = new Dimension(30, 30);
        } else {
            int padding = SystemUtils.isMac() ? 60 : 40;
            dim = new Dimension(textWidth + padding, 34);
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

    private Font getOptimalFont(String text, boolean isIconMode) {
        if (SystemUtils.isMac()) {
            var size = isIconMode ? 18 : 14;
            return new Font(".AppleSystemUIFont", Font.PLAIN, size);
        }

        if (SystemUtils.isWindows()) {
            if (containsEmoji(text)) {
                String[] emojiCandidates = {
                        "Segoe UI Emoji",
                        "Segoe UI Symbol",
                        "Arial Unicode MS",
                        "MS Gothic"
                };

                var availableFonts = GraphicsEnvironment
                        .getLocalGraphicsEnvironment()
                        .getAvailableFontFamilyNames();

                for (String fontName : emojiCandidates) {
                    for (String available : availableFonts) {
                        if (available.equalsIgnoreCase(fontName)) {
                            var font = new Font(fontName, Font.PLAIN, 14);

                            if (font.canDisplayUpTo(text) == -1 || font.canDisplayUpTo(text) > 0) {
                                return font;
                            }
                        }
                    }
                }

                return new Font("Segoe UI Emoji", Font.PLAIN, 14);
            } else {
                return new Font("Segoe UI", Font.PLAIN, 13);
            }
        }

        return new Font(Font.SANS_SERIF, Font.PLAIN, 13);
    }

    private boolean containsEmoji(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        return text.codePoints().anyMatch(codePoint -> codePoint > 0x1F000 ||
                (codePoint >= 0x2600 && codePoint <= 0x27BF) ||
                (codePoint >= 0x2300 && codePoint <= 0x23FF) ||
                Character.getType(codePoint) == Character.OTHER_SYMBOL);
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
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

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
        var frc = g.getFontRenderContext();
        var layout = new TextLayout(text, getFont(), frc);
        var bounds = layout.getBounds();
        float textX = (w - (float) bounds.getWidth()) / 2f;
        float textY = (h + layout.getAscent() - layout.getDescent()) / 2f;
        layout.draw(g, textX, textY);
    }
}
