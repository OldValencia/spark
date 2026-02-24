package io.loom.app.ui.topbar.utils;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import io.loom.app.config.AiConfiguration;
import io.loom.app.ui.topbar.components.AiDock;
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

    private static javafx.scene.image.Image renderSvgToFx(com.formdev.flatlaf.extras.FlatSVGIcon icon) {
        int padding = 2;
        int innerSize = AiDock.ICON_SIZE - padding * 2;

        var scaledIcon = icon.derive(innerSize, innerSize);
        var img = new java.awt.image.BufferedImage(AiDock.ICON_SIZE, AiDock.ICON_SIZE, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var g = img.createGraphics();

        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);

        scaledIcon.paintIcon(null, g, padding, padding);
        g.dispose();

        return javafx.embed.swing.SwingFXUtils.toFXImage(img, null);
    }

    private static Image resizeToFx(BufferedImage img) {
        var resized = new BufferedImage(AiDock.ICON_SIZE, AiDock.ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
        var g = resized.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(img, 0, 0, AiDock.ICON_SIZE, AiDock.ICON_SIZE, null);
        g.dispose();

        return SwingFXUtils.toFXImage(resized, null);
    }

    private static Image createPlaceholderIcon() {
        return new WritableImage(1, 1);
    }
}
