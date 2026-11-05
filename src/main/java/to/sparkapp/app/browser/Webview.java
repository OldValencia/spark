package to.sparkapp.app.browser;

import to.sparkapp.app.utils.SystemUtils;
import java.io.Closeable;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static to.sparkapp.app.browser.WebviewNative.*;

/**
 * A modern, safe Java wrapper for the native Webview library.
 * Manages the C++ pointers and protects JNA callbacks from Java Garbage Collection.
 */
class Webview implements Closeable {

    private final long nativePointer;
    private boolean destroyed;
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
        this.nativePointer = WEBVIEW_NATIVE.webview_create(debug, null);
        if (nativePointer == 0) throw new IllegalStateException("Could not create browser");
    }

    /**
     * Retrieves the native OS window handle (HWND on Windows, NSWindow on Mac).
     *
     * @return The native window pointer.
     */
    long getNativeWindowPointer() {
        return WEBVIEW_NATIVE.webview_get_window(nativePointer);
    }

    /**
     * Navigates the webview to the specified URL.
     *
     * @param url The target URL (e.g., "https://google.com").
     */
    void loadURL(String url) {
        WEBVIEW_NATIVE.webview_navigate(nativePointer, url != null ? url : "about:blank");
    }

    /**
     * Injects JavaScript code to be executed on every new page load (before window.onload).
     *
     * @param script The JavaScript code to inject.
     */
    void setInitScript(String script) {
        String safeScript = String.format(
                "(() => { try { %s } catch (e) { console.error('[Webview] Init Script Error:', e); } })();",
                script
        );
        WEBVIEW_NATIVE.webview_init(nativePointer, safeScript);
    }

    /**
     * Evaluates arbitrary JavaScript code asynchronously in the current page.
     *
     * @param script The JavaScript code to execute.
     */
    void eval(String script) {
        dispatch(() -> {
            String safeScript = String.format(
                    "try { %s } catch (e) { console.error('[Webview] Eval Error:', e); }",
                    script
            );
            WEBVIEW_NATIVE.webview_eval(nativePointer, safeScript);
        });
    }

    /**
     * Binds a Java function so it can be called globally from JavaScript.
     * e.g., binding "myFunc" allows JS to call `window.myFunc(123)`.
     *
     * @param name     The name of the function to expose in JS.
     * @param callback The Java code to execute when JS calls the function.
     */
    void bind(String name, JsCallback callback) {
        BindCallback nativeCallback = new BindCallback() {
            @Override
            public void callback(long seq, String req, long arg) {
                try {
                    var result = callback.apply(req);
                    WEBVIEW_NATIVE.webview_return(nativePointer, seq, false, result != null ? result : "null");
                } catch (Throwable e) {
                    e.printStackTrace();
                    var stackTrace = getStackTraceAsString(e);
                    // Return the error safely to the JS Promise
                    WEBVIEW_NATIVE.webview_return(nativePointer, seq, true, "\"" + stackTrace.replace("\"", "\\\"").replace("\n", "\\n") + "\"");
                }
            }
        };

        // Shield the callback from GC!
        activeBinds.put(name, nativeCallback);
        WEBVIEW_NATIVE.webview_bind(nativePointer, name, nativeCallback, 0);
    }

    /**
     * Safely dispatches a runnable to be executed on the Webview's native thread.
     */
    synchronized void dispatch(Runnable handler) {
        if (destroyed) return;
        var holder = new DispatchCallback[1];

        holder[0] = new DispatchCallback() {
            @Override
            public void callback(long pointer, long arg) {
                try {
                    handler.run();
                } finally {
                    // Remove self from GC shield after execution
                    activeDispatches.remove(holder[0]);
                }
            }
        };

        // Shield the callback from GC!
        activeDispatches.add(holder[0]);
        WEBVIEW_NATIVE.webview_dispatch(nativePointer, holder[0], 0);
    }

    /**
     * Starts the native webview event loop.
     * This is a BLOCKING call and will freeze the thread until the webview is destroyed.
     */
    void run() {
        try {
            WEBVIEW_NATIVE.webview_run(nativePointer);
        } finally {
            destroy();
        }
    }

    synchronized void destroy() {
        if (destroyed) return;
        try {
            if (windows != null) windows.close();
        } finally {
            destroyed = true;
            WEBVIEW_NATIVE.webview_destroy(nativePointer);
            activeDispatches.clear();
            activeBinds.clear();
        }
    }

    /**
     * Gracefully terminates the webview event loop and closes the window.
     */
    @Override
    public synchronized void close() {
        if (destroyed) return;
        WEBVIEW_NATIVE.webview_terminate(nativePointer);
    }

    WindowsBrowser windows() {
        if (!SystemUtils.isWindows()) throw new UnsupportedOperationException("Native browser operations require Windows");
        if (windows == null) windows = new WindowsBrowser(WEBVIEW_NATIVE.webview_get_native_handle(nativePointer, 2));
        return windows;
    }

    private static String getStackTraceAsString(Throwable e) {
        var sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString().trim().replace("\r", "");
    }
}
