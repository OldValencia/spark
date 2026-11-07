package to.sparkapp.app.browser;

import com.sun.jna.Callback;
import com.sun.jna.CallbackReference;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Guid.GUID;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary.StdCallCallback;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * A one-shot COM object pinned only while Java or native code owns a reference.
 */
final class BrowserAsyncCallback implements AutoCloseable {
    private static final Map<Long, BrowserAsyncCallback> LIVE = new ConcurrentHashMap<>();
    private final byte[] iid;
    private static final byte[] UNKNOWN = guidBytes("00000000-0000-0000-C000-000000000046");

    private interface Query extends StdCallCallback {
        int invoke(Pointer self, Pointer iid, PointerByReference out);
    }

    private interface Ref extends StdCallCallback {
        int invoke(Pointer self);
    }

    private interface Completion extends StdCallCallback {
        int invoke(Pointer self, int result, Pointer value);
    }

    // Shared, permanently retained trampolines remain valid even when the final
    // Release drops an instance while its native-to-Java call is returning.
    private static final Query QUERY = (self, iid, out) -> {
        if (out == null) {
            return 0x80004003;
        }

        out.setValue(null);

        if (iid == null) {
            return 0x80004003;
        }

        var callback = LIVE.get(Pointer.nativeValue(self));

        if (callback == null) {
            return 0x8000FFFF;
        }

        byte[] requested = iid.getByteArray(0, 16);

        if (!Arrays.equals(requested, callback.iid) && !Arrays.equals(requested, UNKNOWN)) {
            return 0x80004002;
        }

        callback.references.incrementAndGet();
        out.setValue(self);

        return 0;
    };
    private static final Ref ADD_REF = self -> {
        var callback = LIVE.get(Pointer.nativeValue(self));

        return callback == null ? 0 : callback.references.incrementAndGet();
    };
    private static final Ref RELEASE = self -> {
        var callback = LIVE.get(Pointer.nativeValue(self));

        return callback == null ? 0 : callback.release();
    };
    private static final Completion INVOKE = (self, result, value) -> {
        var callback = LIVE.get(Pointer.nativeValue(self));

        if (callback == null) {
            return 0x8000FFFF;
        }
        // A user completion may re-enter native teardown and release references.

        callback.references.incrementAndGet();

        try {
            if (callback.completed.compareAndSet(false, true)) {
                callback.completion.accept(result, value);
            }

            return 0;
        } catch (Throwable failure) {
            return 0x80004005;
        } finally {
            callback.release();
        }
    };
    private static final Callback[] CALLBACKS = {QUERY, ADD_REF, RELEASE, INVOKE};
    private static final Memory TABLE = createTable();

    private final Memory pointer = new Memory(Native.POINTER_SIZE);
    private final AtomicInteger references = new AtomicInteger(1);
    private final AtomicBoolean ownerReleased = new AtomicBoolean();
    private final AtomicBoolean completed = new AtomicBoolean();
    private final BiConsumer<Integer, Pointer> completion;

    BrowserAsyncCallback(String iid, BiConsumer<Integer, Pointer> completion) {
        this.iid = guidBytes(iid);
        this.completion = completion;
        pointer.setPointer(0, TABLE);
        LIVE.put(Pointer.nativeValue(pointer), this);
    }

    Pointer pointer() {
        return pointer;
    }

    static int retainedCount() {
        return LIVE.size();
    }

    /**
     * Drops Java's submission reference; an asynchronous callee retains its own.
     */
    @Override
    public void close() {
        if (ownerReleased.compareAndSet(false, true)) {
            release();
        }
    }

    private int release() {
        int remaining = references.decrementAndGet();

        if (remaining == 0) {
            LIVE.remove(Pointer.nativeValue(pointer), this);
        }

        return remaining;
    }

    private static Memory createTable() {
        var table = new Memory(4L * Native.POINTER_SIZE);

        for (int i = 0; i < CALLBACKS.length; i++) {
            table.setPointer((long) i * Native.POINTER_SIZE, CallbackReference.getFunctionPointer(CALLBACKS[i]));
        }

        return table;
    }

    private static byte[] guidBytes(String value) {
        var guid = new GUID(value);
        guid.write();

        return guid.getPointer().getByteArray(0, 16);
    }
}
