package to.sparkapp.app.browser;

import java.util.List;

import com.sun.jna.ptr.PointerByReference;

import static to.sparkapp.app.browser.ComCalls.call;
import static to.sparkapp.app.browser.ComCalls.check;
import static to.sparkapp.app.browser.ComCalls.release;

/**
 * Keeps every top-level page of one site at the site's zoom. Runs on the owning WebView2 thread.
 *
 * <p>The zoom is set on the WebView2 controller, so it covers frames and survives navigation.
 */
final class BrowserPageZoom {
    private static final int CORE_GET_SETTINGS = 3;
    private static final int SETTINGS_PUT_IS_ZOOM_CONTROL_ENABLED = 18;
    private static final int CONTROLLER_PUT_ZOOM_FACTOR = 8;

    private double factor = 1.0;

    /** Prepares a new page before its first navigation. */
    void prepare(BrowserContext context) {
        disableBuiltInZoom(context);
        apply(context);
    }

    void setFactor(double factor, List<BrowserContext> contexts) {
        this.factor = factor;
        contexts.forEach(this::apply);
    }

    void apply(BrowserContext context) {
        if (!context.isClosed()) {
            check(call(context.getController(), CONTROLLER_PUT_ZOOM_FACTOR, factor));
        }
    }

    /**
     * Spark steps the zoom itself. WebView2's own Ctrl + wheel zoom would use other steps and
     * reset on the next navigation.
     */
    private static void disableBuiltInZoom(BrowserContext context) {
        var settings = new PointerByReference();
        check(call(context.getCore(), CORE_GET_SETTINGS, settings));

        try {
            check(call(settings.getValue(), SETTINGS_PUT_IS_ZOOM_CONTROL_ENABLED, 0));
        } finally {
            release(settings.getValue());
        }
    }
}
