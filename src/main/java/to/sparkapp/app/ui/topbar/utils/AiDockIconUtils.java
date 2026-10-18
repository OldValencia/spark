package to.sparkapp.app.ui.topbar.utils;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import to.sparkapp.app.config.AiConfiguration;
import to.sparkapp.app.ui.topbar.components.AiDock;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

@Slf4j
public class AiDockIconUtils {

    /**
     * Icons are rasterised at three times their layout size so that they stay sharp on
     * HiDPI monitors (up to 300 % scaling); the ImageView scales them back down.
     */
    private static final int RENDER_SCALE = 3;
    private static final int RENDER_SIZE = AiDock.ICON_SIZE * RENDER_SCALE;

    public static void preloadIcon(AiConfiguration.AiConfig cfg, File userIconsDir) {
        if (cfg.icon() == null) {
            return;
        }

        AiDock.ICON_CACHE.computeIfAbsent(cfg.icon(), key -> {
            try {
                var resourceUrl = AiDock.class.getResource("/icons/" + key);
                if (resourceUrl != null) {
                    if (key.toLowerCase().endsWith(".svg")) {
                        var icon = new FlatSVGIcon(resourceUrl);
                        return renderSvgToFx(icon);
                    } else {
                        return resizeToFx(ImageIO.read(resourceUrl));
                    }
                }

                var userIconFile = new File(userIconsDir, key);
                if (userIconFile.exists()) {
                    if (key.toLowerCase().endsWith(".svg")) {
                        var icon = new FlatSVGIcon(userIconFile);
                        return renderSvgToFx(icon);
                    } else {
                        return resizeToFx(ImageIO.read(userIconFile));
                    }
                }

                log.warn("Icon not found: {} (searched in resources and {})", key, userIconsDir.getAbsolutePath());
                return createPlaceholderIcon();

            } catch (Exception e) {
                log.error("Error while trying to preload icon for {}", cfg.name(), e);
                return createPlaceholderIcon();
            }
        });
    }

    private static Image renderSvgToFx(FlatSVGIcon icon) {
        int padding = 2 * RENDER_SCALE;
        int innerSize = RENDER_SIZE - padding * 2;

        var scaledIcon = icon.derive(innerSize, innerSize);
        var img = new BufferedImage(RENDER_SIZE, RENDER_SIZE, BufferedImage.TYPE_INT_ARGB);
        var g = img.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        scaledIcon.paintIcon(null, g, padding, padding);
        g.dispose();

        return SwingFXUtils.toFXImage(img, null);
    }

    private static Image resizeToFx(BufferedImage img) {
        var resized = new BufferedImage(RENDER_SIZE, RENDER_SIZE, BufferedImage.TYPE_INT_ARGB);
        var g = resized.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(img, 0, 0, RENDER_SIZE, RENDER_SIZE, null);
        g.dispose();

        return SwingFXUtils.toFXImage(resized, null);
    }

    private static Image createPlaceholderIcon() {
        return new WritableImage(1, 1);
    }
}
