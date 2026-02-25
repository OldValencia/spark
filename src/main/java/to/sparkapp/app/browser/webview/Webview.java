package to.sparkapp.app.browser.webview;

import co.casterlabs.commons.platform.Platform;
import com.sun.jna.Native;
import com.sun.jna.ptr.PointerByReference;
import lombok.NonNull;

import java.awt.*;
import java.io.Closeable;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static to.sparkapp.app.browser.webview.WebviewNative.*;

public class Webview implements Closeable, Runnable {

    @Deprecated
    public long $pointer;

    // --- ВАЖНО: Щиты от сборщика мусора (GC) для JNA коллбэков ---
    private final Map<String, BindCallback> activeBinds = new ConcurrentHashMap<>();
    private final Set<DispatchCallback> activeDispatches = ConcurrentHashMap.newKeySet();
    // --------------------------------------------------------------

    public Webview(boolean debug) {
        this(debug, (PointerByReference) null);
    }

    public Webview(boolean debug, int width, int height) {
        this(debug, null, width, height);
    }

    public Webview(boolean debug, @NonNull Component target) {
        this(debug, new PointerByReference(Native.getComponentPointer(target)));
    }

    @Deprecated
    public Webview(boolean debug, PointerByReference windowPointer) {
        this(debug, windowPointer, 800, 600);
    }

    @Deprecated
    public Webview(boolean debug, PointerByReference windowPointer, int width, int height) {
        $pointer = N.webview_create(debug, windowPointer);

        this.loadURL(null);
        this.setSize(width, height);
    }

    @Deprecated
    public long getNativeWindowPointer() {
        return N.webview_get_window($pointer);
    }

    public void setHTML(String html) {
        N.webview_set_html($pointer, html);
    }

    public void loadURL(String url) {
        if (url == null) {
            url = "about:blank";
        }

        N.webview_navigate($pointer, url);
    }

    public void setTitle(@NonNull String title) {
        N.webview_set_title($pointer, title);
    }

    public void setMinSize(int width, int height) {
        N.webview_set_size($pointer, width, height, WV_HINT_MIN);
    }

    public void setMaxSize(int width, int height) {
        N.webview_set_size($pointer, width, height, WV_HINT_MAX);
    }

    public void setSize(int width, int height) {
        N.webview_set_size($pointer, width, height, WV_HINT_NONE);
    }

    public void setFixedSize(int width, int height) {
        N.webview_set_size($pointer, width, height, WV_HINT_FIXED);
    }

    public void setInitScript(@NonNull String script) {
        this.setInitScript(script, false);
    }

    public void setInitScript(@NonNull String script, boolean allowNestedAccess) {
        script = String.format(
                "(() => {\n"
                        + "try {\n"
                        + "if (window.top == window.self || %b) {\n"
                        + "%s\n"
                        + "}\n"
                        + "} catch (e) {\n"
                        + "console.error('[Webview]', 'An error occurred whilst evaluating init script:', %s, e);\n"
                        + "}\n"
                        + "})();",
                allowNestedAccess,
                script,
                '"' + _WebviewUtil.jsonEscape(script) + '"'
        );

        N.webview_init($pointer, script);
    }

    public void eval(@NonNull String script) {
        this.dispatch(() -> {
            N.webview_eval(
                    $pointer,
                    String.format(
                            "try {\n"
                                    + "%s\n"
                                    + "} catch (e) {\n"
                                    + "console.error('[Webview]', 'An error occurred whilst evaluating script:', %s, e);\n"
                                    + "}",
                            script,
                            '"' + _WebviewUtil.jsonEscape(script) + '"'
                    )
            );
        });
    }

    public void bind(@NonNull String name, @NonNull WebviewBindCallback handler) {
        // Создаем нативный коллбэк
        BindCallback nativeCallback = new BindCallback() {
            @Override
            public void callback(long seq, String req, long arg) {
                try {
                    req = _WebviewUtil.forceSafeChars(req);

                    String result = handler.apply(req);
                    if (result == null) {
                        result = "null";
                    }

                    N.webview_return($pointer, seq, false, _WebviewUtil.forceSafeChars(result));
                } catch (Throwable e) {
                    e.printStackTrace();

                    String exceptionJson = '"' + _WebviewUtil.jsonEscape(_WebviewUtil.getExceptionStack(e)) + '"';

                    N.webview_return($pointer, seq, true, exceptionJson);
                }
            }
        };

        // УДЕРЖИВАЕМ В ПАМЯТИ! Чтобы C++ не упал с Invalid memory access
        activeBinds.put(name, nativeCallback);
        N.webview_bind($pointer, name, nativeCallback, 0);
    }

    public void unbind(@NonNull String name) {
        N.webview_unbind($pointer, name);
        // Отпускаем из памяти
        activeBinds.remove(name);
    }

    @Deprecated
    public void dispatch(@NonNull Runnable handler) {
        // Создаем массив-оболочку, чтобы лямбда могла удалить саму себя из Set
        DispatchCallback[] callbackHolder = new DispatchCallback[1];

        callbackHolder[0] = new DispatchCallback() {
            @Override
            public void callback(long pointer, long arg) {
                try {
                    handler.run();
                } finally {
                    // Удаляем себя из памяти после успешного выполнения
                    activeDispatches.remove(callbackHolder[0]);
                }
            }
        };

        // УДЕРЖИВАЕМ В ПАМЯТИ!
        activeDispatches.add(callbackHolder[0]);
        N.webview_dispatch($pointer, callbackHolder[0], 0);
    }

    @Override
    public void run() {
        N.webview_run($pointer);
        N.webview_destroy($pointer);
    }

    public void runAsync() {
        Thread t = new Thread(this);
        t.setDaemon(false);
        t.setName("Webview RunAsync Thread - #" + this.hashCode());
        t.start();
    }

    @Override
    public void close() {
        N.webview_terminate($pointer);
    }

    public void setDarkAppearance(boolean shouldAppearDark) {
        switch (Platform.osFamily) {
            case WINDOWS:
                _WindowsHelper.setWindowAppearance(this, shouldAppearDark);
                break;
            default: // NOOP
                break;
        }
    }

    public static String getVersion() {
        byte[] bytes = N.webview_version().version_number;
        int length = 0;
        for (byte b : bytes) {
            if (b == 0) {
                break;
            }
            length++;
        }
        return new String(bytes, 0, length);
    }
}
