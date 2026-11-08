package to.sparkapp.app.browser;

import static to.sparkapp.app.browser.ComCalls.call;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.ptr.PointerByReference;
import lombok.RequiredArgsConstructor;

/**
 * Receives document size changes pushed by the popup page. Polling is only a fallback until the
 * first pushed message arrives, so an idle popup costs no script execution.
 */
@RequiredArgsConstructor
final class ExtensionPopupMeasurement {
    private static final int CORE_ADD_WEB_MESSAGE_RECEIVED = 34;
    private static final int CORE_REMOVE_WEB_MESSAGE_RECEIVED = 35;
    private static final int MESSAGE_TRY_GET_STRING = 5;
    private static final String WEB_MESSAGE_RECEIVED = "57213f19-00e6-49fa-8e07-898ea01ecbd2";

    private final Consumer<ExtensionPopupSize> measured;
    private final Consumer<Throwable> failed;
    private final ExtensionPopupDiagnostics diagnostics;
    private final ExtensionPopupSizeStability stability = new ExtensionPopupSizeStability();
    private final AtomicBoolean pollPending = new AtomicBoolean();
    private volatile boolean pushed;

    /** Must run on the browser thread before the first navigation. */
    void install(Webview view) {
        view.setInitScript(ExtensionPopupSize.SCRIPT);
        var context = view.windows().activeContext();
        context.getEvents().listen(context.getCore(), CORE_ADD_WEB_MESSAGE_RECEIVED, CORE_REMOVE_WEB_MESSAGE_RECEIVED,
                WEB_MESSAGE_RECEIVED, this::receive);
    }

    boolean isPolling() {
        return !pushed;
    }

    /** Fallback measurement; accepted only after two consecutive matching results. */
    void poll(Webview view) {
        if (pushed || !pollPending.compareAndSet(false, true)) {
            return;
        }

        try {
            BrowserScript.execute(core(view), ExtensionPopupSize.POLL_SCRIPT, this::acceptPolled);
        } catch (RuntimeException error) {
            pollPending.set(false);
            throw error;
        }
    }

    /** Asks the page to report again, for example after the host finished animating its size. */
    void remeasure(Webview view) {
        BrowserScript.execute(core(view), ExtensionPopupSize.REMEASURE_SCRIPT, ignored -> {});
    }

    private void acceptPolled(String json) {
        try {
            var size = json == null ? null : ExtensionPopupSize.parse(json);
            if (size != null && stability.accept(size)) {
                deliver(json, size);
            }
        } catch (Exception error) {
            failed.accept(error);
        } finally {
            pollPending.set(false);
        }
    }

    private void receive(Pointer args) {
        var message = messageText(args);
        if (message == null || !message.startsWith(ExtensionPopupSize.MESSAGE_PREFIX)) {
            return;
        }

        pushed = true;
        var json = message.substring(ExtensionPopupSize.MESSAGE_PREFIX.length());
        try {
            var size = ExtensionPopupSize.parse(json);
            if (size != null) {
                deliver(json, size);
            }
        } catch (Exception error) {
            failed.accept(error);
        }
    }

    /** Returns the message text, or null when the page posted a non-string value. */
    private static String messageText(Pointer args) {
        var value = new PointerByReference();
        if (call(args, MESSAGE_TRY_GET_STRING, value) < 0 || value.getValue() == null) {
            return null;
        }

        try {
            return value.getValue().getWideString(0);
        } finally {
            Ole32.INSTANCE.CoTaskMemFree(value.getValue());
        }
    }

    private void deliver(String json, ExtensionPopupSize size) {
        diagnostics.measurement(json);
        measured.accept(size);
    }

    private static Pointer core(Webview view) {
        return view.windows().activeContext().getCore();
    }
}
