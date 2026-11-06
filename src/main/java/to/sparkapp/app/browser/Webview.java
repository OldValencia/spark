package to.sparkapp.app.browser;

import com.fasterxml.jackson.databind.node.TextNode;
import com.sun.jna.Pointer;

import java.io.Closeable;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;
import to.sparkapp.app.browser.WebviewNative.BindCallback;
import to.sparkapp.app.browser.WebviewNative.DispatchCallback;
import to.sparkapp.app.utils.SystemUtils;

/**
 * Owns the native browser and keeps its callbacks alive through native teardown.
 */
@Slf4j
class Webview implements Closeable {

    private final WebviewNative api;
    private final Pointer nativePointer;
    private final Thread owner = Thread.currentThread();
    private volatile boolean destroyed;
    private WindowsBrowser windows;

    private final Map<String, BindCallback> activeBinds = new ConcurrentHashMap<>();
    private final Set<DispatchCallback> activeDispatches = ConcurrentHashMap.newKeySet();

    /**
     * Functional interface for JavaScript-to-Java binding callbacks.
     */
    interface JsCallback {
        /**
         * @param jsonArgs A JSON string containing an array of arguments from JS.
         * @return A JSON string to be returned to the JS Promise.
         */
        String apply(String jsonArgs) throws Throwable;
    }

    /**
     * Creates a new standalone Webview instance.
     *
     * @param debug Enables browser developer tools/inspector if true.
     */
    Webview(boolean debug) {
        this(debug, WebviewNative.instance());
    }

    Webview(boolean debug, WebviewNative api) {
        this.api = api;
        this.nativePointer = api.webview_create(debug ? 1 : 0, null);

        if (nativePointer == null) {
            throw new IllegalStateException("Could not create browser");
        }
    }

    /**
     * Retrieves the native OS window handle (HWND on Windows, NSWindow on Mac).
     *
     * @return The native window pointer.
     */
    long getNativeWindowPointer() {
        requireOwner();

        return Pointer.nativeValue(api.webview_get_window(nativePointer));
    }

    /**
     * Navigates the webview to the specified URL.
     *
     * @param url The target URL (e.g., "https://google.com").
     */
    void loadURL(String url) {
        requireOwner();
        check("navigate", api.webview_navigate(nativePointer, url != null ? url : "about:blank"));
    }

    /**
     * Injects JavaScript code to be executed on every new page load (before window.onload).
     *
     * @param script The JavaScript code to inject.
     */
    void setInitScript(String script) {
        requireOwner();

        String safeScript = String.format(
                "(() => { try { %s } catch (e) { console.error('[Webview] Init Script" + " Error:', e); } })();",
                script);
        check("init", api.webview_init(nativePointer, safeScript));
    }

    /**
     * Evaluates arbitrary JavaScript code asynchronously in the current page.
     *
     * @param script The JavaScript code to execute.
     */
    void eval(String script) {
        dispatch(() -> {
            String safeScript = String.format(
                    "try { %s } catch (e) { console.error('[Webview] Eval Error:'," + " e); }", script);
            check("eval", api.webview_eval(nativePointer, safeScript));
        });
    }

    /**
     * Binds a Java function so it can be called globally from JavaScript. e.g., binding "myFunc"
     * allows JS to call `window.myFunc(123)`.
     *
     * @param name     The name of the function to expose in JS.
     * @param callback The Java code to execute when JS calls the function.
     */
    void bind(String name, JsCallback callback) {
        requireOwner();

        BindCallback nativeCallback = (seq, req, arg) -> {
            String result;
            int status;

            try {
                result = callback.apply(req);

                if (result == null) {
                    result = "null";
                }

                status = 0;
            } catch (Throwable failure) {
                log.error("JavaScript binding failed: {}", name, failure);
                result = TextNode.valueOf(getStackTraceAsString(failure)).toString();
                status = 1;
            }

            try {
                check("return", api.webview_return(nativePointer, seq, status, result));
            } catch (RuntimeException failure) {
                log.error("Could not complete JavaScript binding: {}", name, failure);
            }
        };
        int result = api.webview_bind(nativePointer, name, nativeCallback, null);
        // DUPLICATE is positive, but the original native callback remains registered.

        if (result != 0) {
            throw nativeFailure("bind " + name, result);
        }

        activeBinds.put(name, nativeCallback);
    }

    /**
     * Safely dispatches a runnable to be executed on the Webview's native thread.
     */
    synchronized void dispatch(Runnable handler) {
        if (destroyed) {
            return;
        }

        var holder = new DispatchCallback[1];

        holder[0] = new DispatchCallback() {
            @Override
            public void callback(Pointer pointer, Pointer arg) {
                try {
                    if (!destroyed) {
                        requireOwner();
                        handler.run();
                    }
                } catch (Throwable failure) {
                    // Java exceptions must not escape through the native callback boundary.
                    log.error("Dispatched browser task failed", failure);
                } finally {
                    // Remove self from GC shield after execution
                    activeDispatches.remove(holder[0]);
                }
            }
        };

        // Shield the callback from GC!
        activeDispatches.add(holder[0]);

        try {
            check("dispatch", api.webview_dispatch(nativePointer, holder[0], null));
        } catch (RuntimeException | Error failure) {
            activeDispatches.remove(holder[0]);
            throw failure;
        }
    }

    /**
     * Starts the native webview event loop. This is a BLOCKING call and will freeze the thread
     * until the webview is destroyed.
     */
    void run() {
        requireOwner();

        try {
            check("run", api.webview_run(nativePointer));
        } finally {
            destroy();
        }
    }

    void destroy() {
        requireOwnerThread();
        synchronized (this) {
            if (destroyed) {
                return;
            }

            destroyed = true;
        }
        // Native teardown can synchronously message the FX host or pump queued
        // callbacks. Never hold the dispatch monitor while waiting for that work.

        try {
            if (windows != null) {
                windows.close();
            }
        } finally {
            try {
                check("destroy", api.webview_destroy(nativePointer));
            } finally {
                activeDispatches.clear();
                activeBinds.clear();
            }
        }
    }

    /**
     * Gracefully terminates the webview event loop and closes the window.
     */
    @Override
    public void close() {
        requireOwnerThread();

        if (destroyed) {
            return;
        }
        // Windows uses PostQuitMessage on the calling thread.

        check("terminate", api.webview_terminate(nativePointer));
    }

    WindowsBrowser windows() {
        requireOwner();

        if (!SystemUtils.isWindows()) {
            throw new UnsupportedOperationException("Native browser operations require Windows");
        }

        if (windows == null) {
            windows = new WindowsBrowser(api.webview_get_native_handle(nativePointer, 2));
        }

        return windows;
    }

    private void requireOwner() {
        requireOwnerThread();

        if (destroyed) {
            throw new IllegalStateException("Browser has been destroyed");
        }
    }

    private void requireOwnerThread() {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException("Native browser access requires its owning thread");
        }
    }

    private static void check(String operation, int result) {
        if (result < 0) {
            throw nativeFailure(operation, result);
        }
    }

    private static IllegalStateException nativeFailure(String operation, int result) {
        return new IllegalStateException("webview_" + operation + " failed (code " + result + ")");
    }

    private static String getStackTraceAsString(Throwable e) {
        var sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));

        return sw.toString().trim().replace("\r", "");
    }
}
