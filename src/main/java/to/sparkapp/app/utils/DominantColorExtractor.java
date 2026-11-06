package to.sparkapp.app.utils;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Picks the colour that represents a site logo, used as the provider accent.
 *
 * <p>The approach is deliberately plain: every sufficiently opaque, sufficiently colourful pixel
 * votes for its hue bucket, the winning bucket is averaged, and the result is lifted to a
 * saturation/brightness that stays readable on the dark UI. Grey-scale logos have no hue to win, so
 * they fall back to a light neutral.
 */
public final class DominantColorExtractor {

    /**
     * 15° per bucket.
     */
    private static final int HUE_BUCKETS = 24;

    private static final double MIN_ALPHA = 128;
    private static final double MIN_SATURATION = 0.20;
    private static final double MIN_BRIGHTNESS = 0.20;
    private static final double MAX_BRIGHTNESS = 0.97;

    /**
     * Keeps the dot visible against the dark background.
     */
    private static final double TARGET_MIN_SATURATION = 0.50;

    private static final double TARGET_MIN_BRIGHTNESS = 0.65;
    private static final double TARGET_MAX_BRIGHTNESS = 0.95;

    private DominantColorExtractor() {
    }

    /**
     * @return hex colour of the image, or {@code null} when it cannot be read
     */
    public static String fromImage(File imageFile) {
        if (imageFile == null || !imageFile.isFile()) {
            return null;
        }

        BufferedImage image;

        try {
            image = ImageIO.read(imageFile);
        } catch (IOException | RuntimeException e) {
            return null;
        }

        if (image == null) {
            return null;
        }

        return fromImage(image);
    }

    public static String fromImage(BufferedImage image) {
        if (image == null) {
            return null;
        }

        var weights = new double[HUE_BUCKETS];
        var sinSum = new double[HUE_BUCKETS];
        var cosSum = new double[HUE_BUCKETS];
        var saturationSum = new double[HUE_BUCKETS];
        var brightnessSum = new double[HUE_BUCKETS];

        double opaquePixels = 0;
        double opaqueBrightnessSum = 0;

        var hsb = new float[3];

        for (var y = 0; y < image.getHeight(); y++) {
            for (var x = 0; x < image.getWidth(); x++) {
                var argb = image.getRGB(x, y);

                if (((argb >>> 24) & 0xFF) < MIN_ALPHA) {
                    continue;
                }

                Color.RGBtoHSB((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, hsb);

                var hue = hsb[0];
                var saturation = hsb[1];
                var brightness = hsb[2];

                opaquePixels++;
                opaqueBrightnessSum += brightness;

                if (saturation < MIN_SATURATION || brightness < MIN_BRIGHTNESS || brightness > MAX_BRIGHTNESS) {
                    continue;
                }

                // Vivid pixels carry more of the brand than washed-out ones.
                var weight = saturation * brightness * (((argb >>> 24) & 0xFF) / 255.0);
                var bucket = Math.min(HUE_BUCKETS - 1, (int) (hue * HUE_BUCKETS));
                var radians = hue * 2 * Math.PI;

                weights[bucket] += weight;
                sinSum[bucket] += Math.sin(radians) * weight;
                cosSum[bucket] += Math.cos(radians) * weight;
                saturationSum[bucket] += saturation * weight;
                brightnessSum[bucket] += brightness * weight;
            }
        }

        var winner = -1;

        for (var i = 0; i < HUE_BUCKETS; i++) {
            if (winner < 0 || weights[i] > weights[winner]) {
                winner = i;
            }
        }

        if (weights[winner] <= 0) {
            return opaquePixels > 0 ? neutralFor(opaqueBrightnessSum / opaquePixels) : null;
        }

        // Averaging the hue as a vector avoids the wrap-around at red.
        var hue = Math.atan2(sinSum[winner], cosSum[winner]) / (2 * Math.PI);

        if (hue < 0) {
            hue += 1.0;
        }

        var saturation = saturationSum[winner] / weights[winner];
        var brightness = brightnessSum[winner] / weights[winner];

        return toHex(hue, Math.max(saturation, TARGET_MIN_SATURATION),
                Math.clamp(brightness, TARGET_MIN_BRIGHTNESS, TARGET_MAX_BRIGHTNESS));
    }

    private static String neutralFor(double averageBrightness) {
        // A grey logo keeps its lightness, only lifted enough to stay visible.
        var brightness = Math.clamp(averageBrightness, 0.70, 0.90);

        return toHex(0, 0, brightness);
    }

    private static String toHex(double hue, double saturation, double brightness) {
        var rgb = Color.HSBtoRGB((float) hue, (float) saturation, (float) brightness);

        return String.format("#%06X", rgb & 0xFFFFFF);
    }
}
