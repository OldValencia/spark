package to.sparkapp.app.windows;

/**
 * A fullscreen app that tests switch on and off by hand, on every monitor at once.
 */
final class FakeFullscreenSensor implements FullscreenSensor {
    private boolean fullscreen;
    private Runnable changed;
    private int shownPills;

    void setFullscreen(boolean fullscreen) {
        this.fullscreen = fullscreen;

        if (changed != null) {
            changed.run();
        }
    }

    boolean isWatching() {
        return changed != null;
    }

    int shownPills() {
        return shownPills;
    }

    @Override
    public boolean isUnderPointer() {
        return fullscreen;
    }

    @Override
    public boolean isOnPillMonitor() {
        return fullscreen;
    }

    @Override
    public void pillShown(long windowHandle) {
        shownPills++;
    }

    @Override
    public void watch(Runnable changed) {
        this.changed = changed;
    }

    @Override
    public void stopWatching() {
        changed = null;
    }
}
