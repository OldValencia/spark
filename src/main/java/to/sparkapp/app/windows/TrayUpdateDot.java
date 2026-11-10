package to.sparkapp.app.windows;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Draws the tab's update dot onto the tray icon, in the same colors. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TrayUpdateDot {
    private static final Color DOT = new Color(0xdf, 0xb5, 0x84);
    private static final Color RING = new Color(0x25, 0x26, 0x2a);
    /** Large enough to read at 16 px, where the icon is drawn in the tray. */
    private static final double DIAMETER = 0.46;
    private static final double RING_WIDTH = 0.07;

    static BufferedImage onto(Image icon) {
        var width = icon.getWidth(null);
        var height = icon.getHeight(null);
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();

        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(icon, 0, 0, null);

            var size = width * DIAMETER;
            var ring = width * RING_WIDTH;
            var dot = new Ellipse2D.Double(width - size - ring / 2, height - size - ring / 2, size, size);
            graphics.setColor(DOT);
            graphics.fill(dot);
            graphics.setColor(RING);
            graphics.setStroke(new BasicStroke((float) ring));
            graphics.draw(dot);
        } finally {
            graphics.dispose();
        }

        return image;
    }
}
