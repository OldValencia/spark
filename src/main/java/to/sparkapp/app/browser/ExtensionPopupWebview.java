package to.sparkapp.app.browser;

import java.util.concurrent.CompletableFuture;

import to.sparkapp.app.utils.NativeWindowUtils;

/**
 * A hidden native browser running its own loop on a dedicated thread. It can be started before a
 * popup is requested, so opening one does not wait for WebView2 controller creation.
 */
final class ExtensionPopupWebview {
    /** Windows' message-only parent: its children are never shown, even when the library shows its window. */
    private static final long HWND_MESSAGE = -3;

    private final CompletableFuture<Webview> ready = new CompletableFuture<>();
    private final CompletableFuture<Void> finished = new CompletableFuture<>();
    private volatile boolean closeRequested;

    private ExtensionPopupWebview() {
    }

    static ExtensionPopupWebview start() {
        var webview = new ExtensionPopupWebview();
        var thread = new Thread(webview::run, "extension-popup");
        thread.setDaemon(true);
        thread.start();

        return webview;
    }

    /** Completes on the browser thread once the hidden browser exists. */
    CompletableFuture<Webview> ready() {
        return ready;
    }

    boolean isFinished() {
        return finished.isDone() || ready.isCompletedExceptionally();
    }

    /** Completes after the browser loop ended and native resources were released. */
    CompletableFuture<Void> finished() {
        return finished;
    }

    /** Ends the browser loop; safe before or after the browser became ready. */
    void close() {
        closeRequested = true;
        ready.thenAccept(view -> view.dispatch(view::close));
    }

    private void run() {
        Webview created = null;
        try {
            created = new Webview(false);
            var handle = created.getNativeWindowPointer();
            NativeWindowUtils.setVisible(handle, false);
            NativeWindowUtils.setParent(handle, HWND_MESSAGE);
            ready.complete(created);
            if (closeRequested) {
                created.close();
            }
            created.run();
        } catch (Throwable error) {
            ready.completeExceptionally(error);
        } finally {
            if (created != null) {
                created.destroy();
            }
            finished.complete(null);
        }
    }
}
