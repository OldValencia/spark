package to.sparkapp.app.windows;

import to.sparkapp.app.config.AppPreferences;

/**
 * Keeps the top edge and the pill out of the way of fullscreen apps. While the pointer stays at the edge, the
 * foreground window is checked at most a few times a second.
 */
final class FullscreenGuard {
    private static final long CHECK_INTERVAL_NANOS = 250_000_000L;

    private final AppPreferences preferences;
    private final FullscreenSensor sensor;
    private boolean fullscreen;
    private long checkedAt;
    private boolean checked;

    FullscreenGuard(AppPreferences preferences, FullscreenSensor sensor) {
        this.preferences = preferences;
        this.sensor = sensor;
    }

    boolean blocksTopEdge() {
        if (!preferences.isTopEdgeQuietInFullscreen()) {
            return false;
        }

        var now = System.nanoTime();

        if (!checked || now - checkedAt >= CHECK_INTERVAL_NANOS) {
            fullscreen = sensor.isUnderPointer();
            checkedAt = now;
            checked = true;
        }

        return fullscreen;
    }

    boolean hidesPill() {
        return preferences.isTopEdgeQuietInFullscreen() && sensor.isOnPillMonitor();
    }

    void pillShown(long windowHandle) {
        sensor.pillShown(windowHandle);
    }

    /** Watches other apps only while the option is on and a pill is meant to stay visible. */
    void watchPill(Runnable changed) {
        if (preferences.isTopEdgeQuietInFullscreen()) {
            sensor.watch(changed);
        } else {
            sensor.stopWatching();
        }
    }

    void stopWatching() {
        sensor.stopWatching();
    }
}
