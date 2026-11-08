package to.sparkapp.app.browser;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Keeps one spare hidden popup browser while the Extensions window is open, so clicking an
 * extension uses an already created browser. Nothing is kept while the window is closed.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ExtensionPopupWebviews {
    private static ExtensionPopupWebview spare;
    private static boolean wanted;

    static synchronized void keepSpare() {
        wanted = true;
        replenish();
    }

    static synchronized void discardSpare() {
        wanted = false;
        if (spare != null) {
            spare.close();
            spare = null;
        }
    }

    /** Returns the spare browser, or starts a new one when none is ready. */
    static synchronized ExtensionPopupWebview take() {
        var usable = spare != null && !spare.isFinished();
        var taken = usable ? spare : ExtensionPopupWebview.start();
        spare = null;

        return taken;
    }

    /** Prepares the next spare after a popup closed, while the Extensions window is still open. */
    static synchronized void replenish() {
        if (spare != null && spare.isFinished()) {
            spare = null;
        }
        if (wanted && spare == null) {
            spare = ExtensionPopupWebview.start();
        }
    }
}
