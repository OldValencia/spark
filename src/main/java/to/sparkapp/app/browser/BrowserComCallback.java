package to.sparkapp.app.browser;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.jna.Callback;
import com.sun.jna.CallbackReference;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Guid.GUID;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary.StdCallCallback;

/**
 * A one-shot COM completion handler, pinned only while Java or native code owns a reference.
 * Subclasses supply the Invoke trampoline for their handler signature.
 */
abstract class BrowserComCallback implements AutoCloseable {
    private static final int S_OK = 0;
    private static final int E_POINTER = 0x80004003;
    private static final int E_NOINTERFACE = 0x80004002;
    private static final int E_UNEXPECTED = 0x8000FFFF;
    private static final int E_FAIL = 0x80004005;
    private static final byte[] UNKNOWN = guidBytes("00000000-0000-0000-C000-000000000046");
    private static final Map<Long, BrowserComCallback> LIVE = new ConcurrentHashMap<>();

    private interface Query extends StdCallCallback {
        int invoke(Pointer self, Pointer iid, PointerByReference out);
    }

    private interface Ref extends StdCallCallback {
        int invoke(Pointer self);
    }

    // Shared, permanently retained trampolines remain valid even when the final
    // Release drops an instance while its native-to-Java call is returning.
    private static final Query QUERY = BrowserComCallback::queryInterface;
    private static final Ref ADD_REF = self -> {
        var callback = LIVE.get(Pointer.nativeValue(self));
        return callback == null ? 0 : callback.references.incrementAndGet();
    };
    private static final Ref RELEASE = self -> {
        var callback = LIVE.get(Pointer.nativeValue(self));
        return callback == null ? 0 : callback.release();
    };

    private final byte[] iid;
    private final Memory pointer = new Memory(Native.POINTER_SIZE);
    private final AtomicInteger references = new AtomicInteger(1);
    private final AtomicBoolean ownerReleased = new AtomicBoolean();
    private final AtomicBoolean completed = new AtomicBoolean();

    protected BrowserComCallback(String iid, Memory table) {
        this.iid = guidBytes(iid);
        pointer.setPointer(0, table);
        LIVE.put(Pointer.nativeValue(pointer), this);
    }

    Pointer pointer() {
        return pointer;
    }

    /** Drops Java's submission reference; an asynchronous callee retains its own. */
    @Override
    public void close() {
        if (ownerReleased.compareAndSet(false, true)) {
            release();
        }
    }

    /** Builds the IUnknown + Invoke vtable shared by every instance of one subclass. */
    protected static Memory vtable(Callback invoke) {
        var callbacks = new Callback[]{QUERY, ADD_REF, RELEASE, invoke};
        var table = new Memory((long) callbacks.length * Native.POINTER_SIZE);
        for (var index = 0; index < callbacks.length; index++) {
            table.setPointer((long) index * Native.POINTER_SIZE, CallbackReference.getFunctionPointer(callbacks[index]));
        }

        return table;
    }

    /** Runs the completion at most once and keeps exceptions inside the native boundary. */
    protected static <T extends BrowserComCallback> int complete(Pointer self, Class<T> type, Completion<T> completion) {
        var callback = LIVE.get(Pointer.nativeValue(self));
        if (!type.isInstance(callback)) {
            return E_UNEXPECTED;
        }

        // A user completion may re-enter native teardown and release references.
        callback.references.incrementAndGet();
        try {
            if (callback.completed.compareAndSet(false, true)) {
                completion.accept(type.cast(callback));
            }
            return S_OK;
        } catch (Throwable failure) {
            return E_FAIL;
        } finally {
            callback.release();
        }
    }

    protected static int retainedCount(Class<? extends BrowserComCallback> type) {
        return (int) LIVE.values().stream().filter(type::isInstance).count();
    }

    private static int queryInterface(Pointer self, Pointer iid, PointerByReference out) {
        if (out == null) {
            return E_POINTER;
        }

        out.setValue(null);
        if (iid == null) {
            return E_POINTER;
        }

        var callback = LIVE.get(Pointer.nativeValue(self));
        if (callback == null) {
            return E_UNEXPECTED;
        }

        var requested = iid.getByteArray(0, 16);
        if (!Arrays.equals(requested, callback.iid) && !Arrays.equals(requested, UNKNOWN)) {
            return E_NOINTERFACE;
        }

        callback.references.incrementAndGet();
        out.setValue(self);

        return S_OK;
    }

    private int release() {
        var remaining = references.decrementAndGet();
        if (remaining == 0) {
            LIVE.remove(Pointer.nativeValue(pointer), this);
        }

        return remaining;
    }

    private static byte[] guidBytes(String value) {
        var guid = new GUID(value);
        guid.write();

        return guid.getPointer().getByteArray(0, 16);
    }

    protected interface Completion<T> {
        void accept(T callback);
    }
}
