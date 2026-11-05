package to.sparkapp.app.browser;

import com.sun.jna.Callback;
import com.sun.jna.CallbackReference;
import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Guid.GUID;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary.StdCallCallback;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** WebView2 COM calls. Every method must run on the owning browser thread.
 * Vtable slots and IIDs follow Microsoft.Web.WebView2 SDK 1.0.2903.40.
 */
@Slf4j
final class WindowsBrowser implements AutoCloseable {
    private final Thread owner = Thread.currentThread();
    private final Pointer controller;
    private final Pointer core;
    private final List<Handler> handlers = new ArrayList<>();
    private final List<Runnable> removals = new ArrayList<>();
    private boolean closed;
    private Pointer memoryController;
    private boolean memoryPolicyChecked;
    private Integer memoryTarget;
    private Boolean visible;
    private boolean browserProcessExited;

    /** Changes resource policy without suspending or navigating the page. */
    void setPresentation(boolean requestedVisible, boolean reduceMemory) {
        requireOwner();
        if (closed) return;
        // Restore the normal target before exposing a page that may have been paged out.
        if (requestedVisible) setMemoryTarget(0);
        if (visible == null || visible != requestedVisible) {
            check(call(controller, 4, requestedVisible ? 1 : 0));
            visible = requestedVisible;
        }
        if (!requestedVisible) setMemoryTarget(reduceMemory ? 1 : 0);
    }

    private void setMemoryTarget(int target) {
        if (!memoryPolicyChecked) {
            memoryPolicyChecked = true;
            var out = new PointerByReference();
            int result = call(core, 0, new GUID("6921f954-79b0-437f-a997-c85811897c68"), out);
            if (result >= 0) memoryController = out.getValue();
            else if (result == 0x80004002) log.debug("This WebView2 Runtime does not support memory targets");
            else log.warn("Memory target interface unavailable: 0x{}", Integer.toHexString(result));
        }
        if (memoryController == null || (memoryTarget != null && memoryTarget == target)) return;
        int result = call(memoryController, 120, target);
        if (result >= 0) memoryTarget = target;
        else {
            // Resource optimization must not make an otherwise usable page inaccessible.
            log.warn("Disabling browser memory targets after error 0x{}", Integer.toHexString(result));
            release(memoryController);
            memoryController = null;
        }
    }

    WindowsBrowser(Pointer controller) {
        if (controller == null) throw new IllegalStateException("WebView2 controller is unavailable");
        this.controller = controller; // Borrowed from webview; do not Release it.
        var out = new PointerByReference();
        check(call(controller, 25, out));
        core = out.getValue();
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
            if (kind.getValue() == 0) browserProcessExited = true;
            failed.accept(kind.getValue());
        });
    }

    private void listen(int add, int remove, String iid, Consumer<Pointer> listener) {
        Event invoke = (self, sender, args) -> {
            try { listener.accept(args); return 0; }
            catch (Throwable failure) { return 0x80004005; }
        };
        var handler = new Handler(iid, invoke);
        handlers.add(handler);
        var token = new LongByReference();
        check(call(core, add, handler.pointer, token));
        removals.add(() -> call(core, remove, token.getValue()));
    }

    void clearBrowsingData(Consumer<Boolean> completed) {
        requireOwner();
        Pointer extended = query(core, "f75f09a8-667e-4983-88d6-c8773f315e84");
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
        try { return out.getValue().getWideString(0); }
        finally { Ole32.INSTANCE.CoTaskMemFree(out.getValue()); }
    }

    boolean focus() {
        requireOwner();
        int result = call(controller, 12, 0);
        // A host can be hidden again while a focus request is queued.
        if (result == 0x80070057) return false;
        check(result);
        return true;
    }
    void stop() { requireOwner(); check(call(core, 43)); }

    @Override public void close() {
        requireOwner();
        if (closed) return;
        closed = true;
        try {
            if (!browserProcessExited) removals.forEach(Runnable::run);
            removals.clear();
            if (!browserProcessExited) check(call(controller, 24)); // A failed browser has already closed its controllers.
        } finally {
            release(memoryController);
            memoryController = null;
            release(core);
        }
        // Handler storage is retained by Webview until native destruction completes.
    }

    private void requireOwner() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("WebView2 COM access requires its owning thread");
    }

    private static Pointer query(Pointer object, String iid) {
        var out = new PointerByReference();
        check(call(object, 0, new GUID(iid), out));
        return out.getValue();
    }

    private static void release(Pointer object) { if (object != null) call(object, 2); }
    private static void check(int result) {
        if (result < 0) throw new IllegalStateException("WebView2 operation failed: 0x" + Integer.toHexString(result));
    }

    private static int call(Pointer object, int slot, Object... arguments) {
        Object[] args = new Object[arguments.length + 1];
        args[0] = object;
        System.arraycopy(arguments, 0, args, 1, arguments.length);
        Pointer function = object.getPointer(0).getPointer((long) slot * Native.POINTER_SIZE);
        return Function.getFunction(function, Function.ALT_CONVENTION).invokeInt(args);
    }

    private interface Query extends StdCallCallback { int invoke(Pointer self, Pointer iid, PointerByReference out); }
    private interface Ref extends StdCallCallback { int invoke(Pointer self); }
    private interface Event extends StdCallCallback { int invoke(Pointer self, Pointer sender, Pointer args); }

    /** Minimal IUnknown + Invoke layout with strongly retained JNA callbacks. */
    private static final class Handler {
        final Memory table = new Memory(4L * Native.POINTER_SIZE);
        final Memory pointer = new Memory(Native.POINTER_SIZE);
        final Callback[] callbacks;

        Handler(String iid, Callback invoke) {
            var supported = new GUID(iid);
            var unknown = new GUID("00000000-0000-0000-C000-000000000046");
            supported.write(); unknown.write();
            Query query = (self, requested, out) -> {
                byte[] bytes = requested.getByteArray(0, 16);
                if (Arrays.equals(bytes, supported.getPointer().getByteArray(0, 16))
                        || Arrays.equals(bytes, unknown.getPointer().getByteArray(0, 16))) {
                    out.setValue(pointer);
                    return 0;
                }
                out.setValue(null);
                return 0x80004002;
            };
            Ref reference = self -> 1;
            callbacks = new Callback[]{query, reference, reference, invoke};
            for (int i = 0; i < callbacks.length; i++) table.setPointer((long) i * Native.POINTER_SIZE,
                    CallbackReference.getFunctionPointer(callbacks[i]));
            pointer.setPointer(0, table);
        }
    }
}
