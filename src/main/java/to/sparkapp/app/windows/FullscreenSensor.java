package to.sparkapp.app.windows;

/**
 * The fullscreen checks the quick access pill needs. Tests replace the native implementation.
 */
interface FullscreenSensor {
    /** A fullscreen app fills the monitor under the pointer. */
    boolean isUnderPointer();

    /** A fullscreen app fills the monitor the pill was last shown on. */
    boolean isOnPillMonitor();

    /** Remembers the monitor of the pill window that was just shown. */
    void pillShown(long windowHandle);

    /** Calls back on the JavaFX thread when another app or its window size may have changed. */
    void watch(Runnable changed);

    void stopWatching();
}
