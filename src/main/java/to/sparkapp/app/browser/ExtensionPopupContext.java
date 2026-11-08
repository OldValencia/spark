package to.sparkapp.app.browser;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Tells the extension which Spark site is selected. The popup document announces the window over a
 * same-origin BroadcastChannel; the prepared background bootstrap listens, so no debugging session,
 * polling, or delayed navigation is needed.
 */
final class ExtensionPopupContext {
    private static final String RELEASE_SCRIPT = "globalThis[Symbol.for('spark.extensionContext')]?.release?.()";
    private static final long RELEASE_TIMEOUT_MILLIS = 300;

    private boolean active;

    /** Must run on the popup's browser thread before navigation. */
    void install(Webview view, String extensionId, int windowId) {
        view.setInitScript(ExtensionContextScript.create(extensionId, windowId));
        active = true;
    }

    /**
     * Restores native window queries in background code, then closes the browser. Closing proceeds
     * after a short timeout even if the page does not answer.
     */
    void closeAndThen(Webview view, Runnable close) {
        if (!active) {
            close.run();
            return;
        }

        var released = new CompletableFuture<Void>();
        released.completeOnTimeout(null, RELEASE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                .thenRun(() -> view.dispatch(close));
        try {
            var core = view.windows().activeContext().getCore();
            BrowserScript.execute(core, RELEASE_SCRIPT, ignored -> released.complete(null));
        } catch (RuntimeException error) {
            released.complete(null);
        }
    }
}
