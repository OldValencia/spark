package to.sparkapp.app.browser;

import java.util.function.BooleanSupplier;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.utils.NativeWindowUtils;
import to.sparkapp.app.utils.SystemUtils;

/**
 * Serializes visibility requests so a queued show cannot override a later hide.
 */
@RequiredArgsConstructor
final class BrowserPresentation {
    private final Object lock = new Object();
    private final AppPreferences preferences;
    @Getter
    @Setter
    private volatile long nativeHandle;
    @Getter
    private volatile boolean hibernated;
    @Getter
    private volatile long generation;
    private volatile long parentHandle;
    private volatile int x;
    private volatile int y;
    private volatile int width;
    private volatile int height;
    private boolean displayRequested;

    void initialize(long parentHandle, int x, int y, int width, int height) {
        this.parentHandle = parentHandle;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    void attach(boolean hideBeforeReady) {
        if (nativeHandle == 0) {
            return;
        }

        if (hibernated) {
            NativeWindowUtils.setVisible(nativeHandle, false);
            NativeWindowUtils.unparent(nativeHandle);
        } else if (parentHandle != 0) {
            NativeWindowUtils.setParent(nativeHandle, parentHandle);

            if (hideBeforeReady) {
                NativeWindowUtils.setVisible(nativeHandle, false);
            }

            NativeWindowUtils.setBounds(nativeHandle, x, y, width, height);
        }
    }

    void updateBounds(int x, int y, int width, int height, boolean ready) {
        if (width <= 0 || height <= 0) {
            return;
        }

        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;

        if (ready && nativeHandle != 0 && !hibernated) {
            reattach();
        }
    }

    void reattach() {
        if (parentHandle != 0 && !NativeWindowUtils.isChildOf(nativeHandle, parentHandle)) {
            NativeWindowUtils.setParent(nativeHandle, parentHandle);
        }
        // SetWindowPos accepts physical pixels; webview_set_size applies DPI scaling again.

        NativeWindowUtils.setBounds(nativeHandle, x, y, width, height);
    }

    void hibernate() {
        synchronized (lock) {
            generation++;
            hibernated = true;
            displayRequested = false;
        }

        if (SystemUtils.isWindows() && nativeHandle != 0) {
            NativeWindowUtils.setVisible(nativeHandle, false);
            // Detach before JavaFX destroys its HWND while retaining the live page and viewport.
            NativeWindowUtils.unparent(nativeHandle);
        }
    }

    void wakeup(long parentHandle, boolean readyToShow) {
        synchronized (lock) {
            generation++;

            if (parentHandle != 0) {
                this.parentHandle = parentHandle;
            }

            hibernated = false;
            displayRequested = readyToShow;
        }
    }

    void requestVisible(boolean visible) {
        synchronized (lock) {
            generation++;
            displayRequested = visible;
        }

        if (!visible && nativeHandle != 0) {
            NativeWindowUtils.setVisible(nativeHandle, false);
        }
    }

    void apply(Webview browser, BooleanSupplier disposed) {
        long requestedGeneration;
        boolean show;
        boolean lowMemory;
        synchronized (lock) {
            requestedGeneration = generation;
            show = displayRequested && !hibernated;
            lowMemory = hibernated && preferences.isBackgroundMemoryReductionEnabled();
        }
        browser.windows().setPresentation(show, lowMemory);
        synchronized (lock) {
            if (requestedGeneration == generation && !disposed.getAsBoolean() && nativeHandle != 0) {
                NativeWindowUtils.setVisible(nativeHandle, show);
            }
        }
    }
}
