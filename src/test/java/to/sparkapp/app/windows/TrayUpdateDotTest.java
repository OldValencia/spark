package to.sparkapp.app.windows;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Test;

class TrayUpdateDotTest {
    @Test
    void contrastingDotSitsAboveTheFaceAndKeepsTheRestOfTheIcon() {
        var icon = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        var graphics = icon.createGraphics();
        graphics.setColor(Color.BLUE);
        graphics.fillRect(0, 0, 64, 64);
        graphics.dispose();

        var marked = TrayUpdateDot.onto(icon);

        assertEquals(64, marked.getWidth());
        assertEquals(Color.BLUE.getRGB(), marked.getRGB(4, 4), "The icon itself stays as it was");
        assertEquals(new Color(0x5a, 0xd8, 0xff).getRGB(), marked.getRGB(52, 12), "The dot is drawn on top");
        assertEquals(Color.BLUE.getRGB(), marked.getRGB(43, 38), "The right eye stays uncovered");
        assertEquals(Color.BLUE.getRGB(), icon.getRGB(52, 12), "The original icon is not changed");
    }
}
