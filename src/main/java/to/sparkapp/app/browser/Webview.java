package to.sparkapp.app.browser;

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

    // --- GC Shields: Prevents the JVM from destroying C++ callbacks ---
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
     * Loads raw HTML content into the webview.
     *
     * @param html The raw HTML string.
     */
    void setHtml(String html) {
        WEBVIEW_NATIVE.webview_set_html(nativePointer, html);
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
     * Resizes the webview window or internal canvas.
     */
    void setSize(int width, int height) {
        WEBVIEW_NATIVE.webview_set_size(nativePointer, width, height, WV_HINT_NONE);
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
     * Removes a previously bound JavaScript function.
     */
    void unbind(String name) {
        WEBVIEW_NATIVE.webview_unbind(nativePointer, name);
        activeBinds.remove(name);
    }

    /**
     * Safely dispatches a runnable to be executed on the Webview's native thread.
     */
    void dispatch(Runnable handler) {
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
        WEBVIEW_NATIVE.webview_run(nativePointer);
        WEBVIEW_NATIVE.webview_destroy(nativePointer);
    }

    /**
     * Gracefully terminates the webview event loop and closes the window.
     */
    @Override
    public void close() {
        WEBVIEW_NATIVE.webview_terminate(nativePointer);
    }

    /**
     * Returns the underlying native library version.
     */
    static String getVersion() {
        var bytes = WEBVIEW_NATIVE.webview_version().version_number;
        var length = 0;
        while (length < bytes.length && bytes[length] != 0) {
            length++;
        }
        return new String(bytes, 0, length);
    }

    private static String getStackTraceAsString(Throwable e) {
        var sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString().trim().replace("\r", "");
    }
}
