package to.sparkapp.app.windows;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.awt.image.BufferedImage;
import java.awt.image.MultiResolutionImage;

import org.junit.jupiter.api.Test;

class TrayIconImageTest {
    @Test
    void theTrayGetsAFrameDrawnForItsOwnSize() {
        var icon = assertInstanceOf(MultiResolutionImage.class, TrayIconImage.load());

        for (var size : new int[] {16, 20, 24, 32}) {
            var frame = (BufferedImage) icon.getResolutionVariant(size, size);

            assertEquals(size, frame.getWidth(), "A " + size + " px tray needs no scaling");
        }
    }

    @Test
    void eachFrameGetsItsOwnDot() {
        var icon = TrayIconImage.load();
        var marked = assertInstanceOf(MultiResolutionImage.class, TrayIconImage.withDot(icon));

        for (var frame : marked.getResolutionVariants()) {
            var image = (BufferedImage) frame;
            var plain = (BufferedImage) ((MultiResolutionImage) icon).getResolutionVariant(image.getWidth(),
                    image.getHeight());
            var x = image.getWidth() * 13 / 16;
            var y = image.getHeight() * 3 / 16;

            assertEquals(plain.getWidth(), image.getWidth());
            assertNotEquals(plain.getRGB(x, y), image.getRGB(x, y), "The dot is drawn at " + image.getWidth() + " px");
        }
    }
}
