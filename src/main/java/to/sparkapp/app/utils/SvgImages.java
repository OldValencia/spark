package to.sparkapp.app.utils;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;

import com.github.weisj.jsvg.parser.SVGLoader;
import com.github.weisj.jsvg.view.ViewBox;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Draws site logos, which use gradients, masks and filters that JavaFX shapes cannot. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SvgImages {
    private static final SVGLoader LOADER = new SVGLoader();

    /**
     * Renders an SVG into a square image, fitted and centered inside the padding.
     *
     * @throws IOException when the file cannot be read as SVG
     */
    public static BufferedImage render(URL svg, int size, int padding) throws IOException {
        var document = LOADER.load(svg);

        if (document == null) {
            throw new IOException("Not a readable SVG: " + svg);
        }

        var image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();

        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            var inner = size - padding * 2f;
            document.render(null, graphics, new ViewBox(padding, padding, inner, inner));
        } finally {
            graphics.dispose();
        }

        return image;
    }
}
