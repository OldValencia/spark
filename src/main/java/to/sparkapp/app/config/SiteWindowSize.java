package to.sparkapp.app.config;

import to.sparkapp.app.config.WindowSizePreset.Slot;

/**
 * The window size of a site that keeps its own: a size preset, which the site follows when the preset changes, the
 * whole screen, or a size set by hand.
 *
 * @param preset the preset the site uses, or {@code null}
 * @param fill   whether the window fills the screen
 * @param width  the width set by hand, when there is no preset and no fill
 * @param height the height set by hand, when there is no preset and no fill
 */
public record SiteWindowSize(Slot preset, boolean fill, int width, int height) {
    public static SiteWindowSize ofPreset(Slot preset) {
        return new SiteWindowSize(preset, false, 0, 0);
    }

    public static SiteWindowSize filling() {
        return new SiteWindowSize(null, true, 0, 0);
    }

    public static SiteWindowSize custom(double width, double height) {
        return new SiteWindowSize(null, false, (int) Math.round(width), (int) Math.round(height));
    }
}
