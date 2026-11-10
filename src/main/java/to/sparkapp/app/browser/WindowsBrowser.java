package to.sparkapp.app.browser;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.sun.jna.Pointer;
import lombok.Setter;

import static to.sparkapp.app.browser.ComCalls.call;
import static to.sparkapp.app.browser.ComCalls.check;

/** All methods run on the owning WebView2 thread. */
final class WindowsBrowser implements AutoCloseable {
    private final Thread owner = Thread.currentThread();
    private final BrowserContext root;
    private final BrowserContextStack pages;
    private final BrowserContextObserver observer;
    private final BrowserShortcutKeys shortcutKeys = new BrowserShortcutKeys();
    private final BrowserTitleWatch titleWatch = new BrowserTitleWatch();
    private boolean closed;
    private boolean browserProcessExited;
    @Setter
    private Consumer<Boolean> extraPageChanged = extra -> {};
    private Consumer<Integer> processFailure = kind -> {};

    WindowsBrowser(Pointer controller, Consumer<Runnable> defer) {
        root = new BrowserContext(controller, null);
        pages = new BrowserContextStack(root, defer, this::contextChanged);
        observer = new BrowserContextObserver(context -> context == pages.getActive() && !closed, kind -> {
            if (kind == 0) {
                browserProcessExited = true;
            }

            processFailure.accept(kind);
        });
        installPage(root);
    }

    void enablePages(String script, Consumer<String> message) {
        requireOwner();
        observer.setMessage(message);
        pages.enable("window.sparkCall=(...args)=>{chrome.webview.postMessage(JSON.stringify(args));return Promise.resolve(null);};\n" + script,
                this::installPage);
    }

    private void installPage(BrowserContext context) {
        observer.install(context);
        shortcutKeys.install(context);
        titleWatch.install(context);
    }

    BrowserContext activeContext() {
        requireOwner();

        return pages.getActive();
    }

    boolean hasExtraPage() {
        return pages.getActive() != root;
    }

    /** Reports the change of page before the new page's address, so the address is not taken for the old page. */
    private void contextChanged(BrowserContext context) {
        extraPageChanged.accept(context != root);
        observer.activate(context);
    }

    void observe(Consumer<Long> starting, BiConsumer<Long, Boolean> completed) {
        requireOwner();
        observer.setStarting(starting);
        observer.setCompleted(completed);
    }

    void observeDocument(Consumer<Long> ready) {
        requireOwner();
        observer.setDocumentReady(ready);
    }

    void observeSource(Consumer<String> changed) {
        requireOwner();
        observer.setSourceChanged(changed);
    }

    void observeTitles(Runnable changed) {
        requireOwner();
        titleWatch.setChanged(changed);
    }

    void observeShortcuts(Consumer<SiteShortcut> pressed) {
        requireOwner();
        shortcutKeys.setPressed(pressed);
    }

    void observeProcessFailures(Consumer<Integer> failed) {
        requireOwner();
        processFailure = failed;
    }

    void setPresentation(boolean visible, boolean lowMemory) {
        requireOwner();

        if (!closed && !browserProcessExited) {
            pages.setPresentation(visible, lowMemory);
        }
    }

    /** Applies one site's zoom to its main page and to the pages it opened. */
    void setZoomFactor(double factor) {
        requireOwner();

        if (!closed && !browserProcessExited) {
            pages.setZoomFactor(factor);
        }
    }

    void setMuted(boolean muted) {
        requireOwner();

        if (!closed && !browserProcessExited) {
            pages.setMuted(muted);
        }
    }

    void setBackgroundMemoryReduction(boolean enabled) {
        requireOwner();
        pages.setReduceInactive(enabled);
    }

    String source() {
        return activeContext().source();
    }

    boolean focus() {
        return activeContext().focus();
    }

    void stop() {
        check(call(activeContext().getCore(), 43));
    }

    void back() {
        requireOwner();
        pages.back();
    }

    void closeExtraPage() {
        requireOwner();
        pages.closePage(pages.getActive());
    }

    void returnToRoot() {
        requireOwner();
        pages.returnToRoot();
    }

    void clearBrowsingData(Consumer<Boolean> completed) {
        requireOwner();
        BrowserProfileData.clear(root.getCore(), completed);
    }

    @Override
    public void close() {
        requireOwner();
        closed = true;
        pages.close(browserProcessExited);
    }

    private void requireOwner() {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException("WebView2 COM access requires its owning thread");
        }
    }
}
