package to.sparkapp.app.browser;

import static to.sparkapp.app.browser.ComCalls.*;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary.StdCallCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

/**
 * WebView2 COM calls. Every method must run on the owning browser thread. Vtable slots and IIDs
 * follow Microsoft.Web.WebView2 SDK 1.0.2903.40.
 */
@Slf4j
final class WindowsBrowser implements AutoCloseable {
    private interface BrowserEvent extends StdCallCallback {
        int invoke(Pointer self, Pointer sender, Pointer args);
    }

    private final Thread owner = Thread.currentThread();
    private final Pointer controller;
    private final Pointer core;
    private final List<BrowserEventCallback> handlers = new ArrayList<>();
    private final List<Runnable> removals = new ArrayList<>();
    private boolean closed;
    private final BrowserMemoryPolicy memoryPolicy;
    private Boolean visible;
    private boolean browserProcessExited;
    private Pointer documentEvents;

    /**
     * Changes resource policy without suspending or navigating the page.
     */
    void setPresentation(boolean requestedVisible, boolean reduceMemory) {
        requireOwner();

        if (closed) {
            return;
        }
        // Restore the normal target before exposing a page that may have been paged out.

        if (requestedVisible) {
            memoryPolicy.setTarget(0);
        }

        if (visible == null || visible != requestedVisible) {
            check(call(controller, 4, requestedVisible ? 1 : 0));
            visible = requestedVisible;
        }

        if (!requestedVisible) {
            memoryPolicy.setTarget(reduceMemory ? 1 : 0);
        }
    }

    WindowsBrowser(Pointer controller) {
        if (controller == null) {
            throw new IllegalStateException("WebView2 controller is unavailable");
        }

        this.controller = controller;
        // Borrowed from webview; do not Release it.

        var out = new PointerByReference();
        check(call(controller, 25, out));
        core = out.getValue();
        memoryPolicy = new BrowserMemoryPolicy(core);
    }

    void observe(Consumer<Long> starting, BiConsumer<Long, Boolean> completed) {
        requireOwner();
        listen(7, 8, "9adbe429-f36d-432b-9ddc-f8881fbd76e3", args -> {
            var id = new LongByReference();
            check(call(args, 9, id));
            starting.accept(id.getValue());
        });
        listen(15, 16, "d33a35bf-1c49-4f98-93ab-006e0533fe1c", args -> {
            var id = new LongByReference();
            var success = new IntByReference();
            check(call(args, 5, id));
            check(call(args, 3, success));
            completed.accept(id.getValue(), success.getValue() != 0);
        });
    }

    void observeProcessFailures(Consumer<Integer> failed) {
        requireOwner();
        listen(25, 26, "79e0aea4-990b-42d9-aa1d-0fcc2e5bc7f1", args -> {
            var kind = new IntByReference();
            check(call(args, 3, kind));

            if (kind.getValue() == 0) {
                browserProcessExited = true;
            }

            failed.accept(kind.getValue());
        });
    }

    void observeDocument(Consumer<Long> ready) {
        requireOwner();
        documentEvents = query(core, "9e8f0cf8-e670-4b5e-b2bc-73e061e3184c");
        listen(documentEvents, 64, 65, "4bac7e9c-199e-49ed-87ed-249303acf019", args -> {
            var id = new LongByReference();
            check(call(args, 3, id));
            ready.accept(id.getValue());
        });
    }

    private void listen(int add, int remove, String iid, Consumer<Pointer> listener) {
        listen(core, add, remove, iid, listener);
    }

    private void listen(Pointer target, int add, int remove, String iid, Consumer<Pointer> listener) {
        BrowserEvent invoke = (self, sender, args) -> {
            try {
                listener.accept(args);

                return 0;
            } catch (Throwable failure) {
                return 0x80004005;
            }
        };
        var handler = new BrowserEventCallback(iid, invoke);
        handlers.add(handler);

        var token = new LongByReference();
        check(call(target, add, handler.pointer, token));
        removals.add(() -> call(target, remove, token.getValue()));
    }

    void clearBrowsingData(Consumer<Boolean> completed) {
        requireOwner();

        var extended = query(core, "f75f09a8-667e-4983-88d6-c8773f315e84");
        Pointer profile = null;
        Pointer profile2 = null;

        try {
            var out = new PointerByReference();
            check(call(extended, 105, out));
            profile = out.getValue();
            profile2 = query(profile, "fa740d4b-5eae-4344-a8ad-74be31925397");

            try (var callback = new ClearBrowsingDataCallback(completed)) {
                check(call(profile2, 12, callback.pointer()));
            }
        } finally {
            release(profile2);
            release(profile);
            release(extended);
        }
    }

    String source() {
        requireOwner();

        var out = new PointerByReference();
        check(call(core, 4, out));

        try {
            return out.getValue().getWideString(0);
        } finally {
            Ole32.INSTANCE.CoTaskMemFree(out.getValue());
        }
    }

    boolean focus() {
        requireOwner();

        int result = call(controller, 12, 0);
        // A host can be hidden again while a focus request is queued.

        if (result == 0x80070057) {
            return false;
        }

        check(result);

        return true;
    }

    void stop() {
        requireOwner();
        check(call(core, 43));
    }

    @Override
    public void close() {
        requireOwner();

        if (closed) {
            return;
        }

        closed = true;

        try {
            if (!browserProcessExited) {
                removals.forEach(Runnable::run);
            }

            removals.clear();

            if (!browserProcessExited) {
                check(call(controller, 24));
            }
            // A failed browser has already closed its controllers.
        } finally {
            release(documentEvents);
            documentEvents = null;
            memoryPolicy.close();
            release(core);
        }
        // Handler storage is retained by Webview until native destruction completes.
    }

    private void requireOwner() {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException("WebView2 COM access requires its owning thread");
        }
    }
}
