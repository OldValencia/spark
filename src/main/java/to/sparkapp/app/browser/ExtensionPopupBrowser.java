package to.sparkapp.app.browser;

import java.util.function.Consumer;

import lombok.AccessLevel;
import lombok.Getter;
import to.sparkapp.app.utils.NativeWindowUtils;

/** Owns the browser loop and synchronizes its viewport with the popup's physical client size. */
final class ExtensionPopupBrowser {
    private final long parent;
    private final Consumer<Throwable> failed;
    private final Runnable dismiss;
    private final ExtensionPopupViewport viewport;
    private final ExtensionPopupMeasurement measurement;
    private final ExtensionPopupContext context;
    @Getter(AccessLevel.PACKAGE)
    private volatile Webview browser;
    private volatile long browserHandle;
    private volatile boolean closed;
    private volatile ExtensionPopupWebview host;

    ExtensionPopupBrowser(long parent, Consumer<ExtensionPopupSize> measured, Consumer<Throwable> failed, Runnable dismiss,
                          ExtensionPopupDiagnostics diagnostics) {
        this.parent = parent;
        this.failed = failed;
        this.dismiss = dismiss;
        viewport = new ExtensionPopupViewport(parent, diagnostics);
        measurement = new ExtensionPopupMeasurement(measured, failed, diagnostics);
        context = new ExtensionPopupContext();
    }

    /** Uses the spare browser when one is ready; setup runs on that browser's own thread. */
    void open(String extensionId, String page, Integer contextWindowId) {
        host = ExtensionPopupWebviews.take();
        host.finished().thenRun(() -> browser = null);
        host.ready().whenComplete((view, error) -> {
            if (error != null) {
                if (!closed) {
                    failed.accept(error);
                }
            } else {
                view.dispatch(() -> attach(view, extensionId, page, contextWindowId));
            }
        });
    }

    /** Called periodically from the JavaFX thread; it only reaches the browser thread when work is needed. */
    void tick() {
        var active = browser;
        if (active == null || closed) {
            return;
        }

        var viewportStale = viewport.needsSynchronization(browserHandle);
        var polling = measurement.isPolling();
        if (viewportStale || polling) {
            active.dispatch(() -> onBrowserThread(active, viewportStale, polling));
        }
    }

    void resizeViewport() {
        dispatchIfOpen(viewport::synchronize);
    }

    void remeasure() {
        dispatchIfOpen(measurement::remeasure);
    }

    void close() {
        closed = true;
        var active = browser;
        if (active != null) {
            active.dispatch(() -> context.closeAndThen(active, host::close));
        } else if (host != null) {
            host.close();
        }
    }

    private void attach(Webview view, String extensionId, String page, Integer contextWindowId) {
        if (closed) {
            host.close();
            return;
        }

        try {
            browser = view;
            browserHandle = view.getNativeWindowPointer();
            NativeWindowUtils.setParent(browserHandle, parent);
            ExtensionPopupEscape.install(view, dismiss);
            ExtensionPopupNavigation.install(view);
            measurement.install(view);
            viewport.synchronize(view);
            load(view, "chrome-extension://" + extensionId + "/" + page, extensionId, contextWindowId);
            NativeWindowUtils.setVisible(browserHandle, true);
            view.dispatch(this::restoreFrame);
        } catch (Exception error) {
            if (!closed) {
                failed.accept(error);
            }
        }
    }

    /** The library's queued default size can restore native frame styles. */
    private void restoreFrame() {
        var active = browser;
        if (closed) {
            close();
        } else if (active != null) {
            NativeWindowUtils.setParent(browserHandle, parent);
            viewport.synchronize(active);
        }
    }

    private void onBrowserThread(Webview active, boolean viewportStale, boolean polling) {
        if (closed) {
            return;
        }

        try {
            if (viewportStale) {
                viewport.synchronize(active);
            }
            if (polling) {
                measurement.poll(active);
            }
        } catch (Exception error) {
            if (!closed) {
                failed.accept(error);
            }
        }
    }

    private void load(Webview view, String url, String extensionId, Integer contextWindowId) {
        if (contextWindowId != null) {
            context.install(view, extensionId, contextWindowId);
        }
        view.loadURL(url);
    }

    private void dispatchIfOpen(Consumer<Webview> action) {
        var active = browser;
        if (active != null && !closed) {
            active.dispatch(() -> {
                if (!closed) {
                    action.accept(active);
                }
            });
        }
    }
}
