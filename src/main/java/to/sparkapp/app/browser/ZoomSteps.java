package to.sparkapp.app.browser;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Spark's zoom scale: the page scale is 1.2 to the power of the level, changed in half-level steps
 * from about 58 % to 207 %.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ZoomSteps {
    static final double DEFAULT_LEVEL = 0.0;
    private static final double STEP = 0.5;
    private static final double MIN_LEVEL = -3.0;
    private static final double MAX_LEVEL = 4.0;
    private static final double BASE = 1.2;

    static double next(double level, boolean increase) {
        return clamp(level + (increase ? STEP : -STEP));
    }

    /** Keeps a stored or edited value usable: out-of-range values are limited, invalid ones reset. */
    static double clamp(double level) {
        if (!Double.isFinite(level)) {
            return DEFAULT_LEVEL;
        }

        return Math.clamp(level, MIN_LEVEL, MAX_LEVEL);
    }

    static double factor(double level) {
        return Math.pow(BASE, level);
    }

    static double percent(double level) {
        return Math.round(factor(level) * 100.0);
    }

    /** The zoom a stored level gives, for a site whose page has not been opened yet. */
    public static double storedPercent(double level) {
        return percent(clamp(level));
    }
}
