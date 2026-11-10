package to.sparkapp.app.windows;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Test;

class TrayUpdateDotTest {
    @Test
    void dotSitsInTheBottomRightCornerAndKeepsTheRestOfTheIcon() {
        var icon = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        var graphics = icon.createGraphics();
        graphics.setColor(Color.BLUE);
        graphics.fillRect(0, 0, 64, 64);
        graphics.dispose();

        var marked = TrayUpdateDot.onto(icon);

        assertEquals(64, marked.getWidth());
        assertEquals(Color.BLUE.getRGB(), marked.getRGB(4, 4), "The icon itself stays as it was");
        assertEquals(new Color(0xdf, 0xb5, 0x84).getRGB(), marked.getRGB(48, 48), "The dot is drawn on top");
        assertEquals(Color.BLUE.getRGB(), icon.getRGB(48, 48), "The original icon is not changed");
    }
}
