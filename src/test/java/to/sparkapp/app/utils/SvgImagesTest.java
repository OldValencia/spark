package to.sparkapp.app.utils;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SvgImagesTest {
    @TempDir
    Path directory;

    @Test
    void bundledLogosRenderWithinThePadding() throws Exception {
        for (var name : new String[]{"chatgpt", "claude", "copilot", "gemini", "mistral", "perplexity"}) {
            var image = SvgImages.render(getClass().getResource("/icons/" + name + ".svg"), 54, 6);

            assertEquals(54, image.getWidth());
            assertNotEquals(0, image.getRGB(27, 27) >>> 24 | opaquePixels(image), name + " must draw something");
            assertEquals(0, image.getRGB(1, 1) >>> 24, name + " keeps the padding clear");
        }
    }

    @Test
    void unreadableFileIsReported() throws Exception {
        var broken = Files.writeString(directory.resolve("broken.svg"), "not svg");

        assertThrows(IOException.class, () -> SvgImages.render(broken.toUri().toURL(), 32, 0));
    }

    private static int opaquePixels(BufferedImage image) {
        var count = 0;

        for (var y = 0; y < image.getHeight(); y++) {
            for (var x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) >>> 24 > 0) {
                    count++;
                }
            }
        }

        return count;
    }
}
