package to.sparkapp.app.windows;

import java.awt.Image;
import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.MultiResolutionImage;
import java.io.IOException;
import java.util.ArrayList;

import javax.imageio.ImageIO;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The tray icon in the sizes the tray uses. The tray draws the frame that matches its size and the display scale;
 * shrinking the large icon to a few pixels instead would drop Sparky's outline and the edges of its eyes.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TrayIconImage {
    private static final int[] SIZES = {16, 20, 24, 32, 40, 48, 64};
    private static final String FRAME = "/app-icons/tray/icon-%d.png";
    private static final String FALLBACK = "/app-icons/icon.png";

    /** @return the icon, or {@code null} when no icon could be read */
    static Image load() {
        var frames = new ArrayList<Image>();

        for (var size : SIZES) {
            var frame = read(FRAME.formatted(size));

            if (frame != null) {
                frames.add(frame);
            }
        }

        if (frames.isEmpty()) {
            return read(FALLBACK);
        }

        return new BaseMultiResolutionImage(frames.toArray(Image[]::new));
    }

    /** The same icon with the update dot drawn onto each frame at its own size. */
    static Image withDot(Image icon) {
        if (!(icon instanceof MultiResolutionImage frames)) {
            return TrayUpdateDot.onto(icon);
        }

        var marked = frames.getResolutionVariants().stream()
                .map(TrayUpdateDot::onto)
                .toArray(Image[]::new);

        return new BaseMultiResolutionImage(marked);
    }

    private static Image read(String resource) {
        var url = TrayIconImage.class.getResource(resource);

        if (url == null) {
            return null;
        }

        try {
            return ImageIO.read(url);
        } catch (IOException | IllegalArgumentException failure) {
            log.warn("Could not read tray icon {}", resource, failure);

            return null;
        }
    }
}
