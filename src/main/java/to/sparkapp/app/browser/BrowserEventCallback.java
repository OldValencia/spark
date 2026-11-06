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

/**
 * Minimal IUnknown + Invoke layout with strongly retained JNA callbacks.
 */
final class BrowserEventCallback {
    private interface Query extends StdCallCallback {
        int invoke(Pointer self, Pointer iid, PointerByReference out);
    }

    private interface Ref extends StdCallCallback {
        int invoke(Pointer self);
    }

    private interface Event extends StdCallCallback {
        int invoke(Pointer self, Pointer sender, Pointer args);
    }

    final Memory table = new Memory(4L * Native.POINTER_SIZE);
    final Memory pointer = new Memory(Native.POINTER_SIZE);
    final Callback[] callbacks;

    BrowserEventCallback(String iid, Callback invoke) {
        var supported = new GUID(iid);
        var unknown = new GUID("00000000-0000-0000-C000-000000000046");
        supported.write();
        unknown.write();

        Query query = (self, requested, out) -> {
            var bytes = requested.getByteArray(0, 16);

            if (Arrays.equals(bytes, supported.getPointer().getByteArray(0, 16)) ||
                    Arrays.equals(bytes, unknown.getPointer().getByteArray(0, 16))) {
                out.setValue(pointer);

                return 0;
            }

            out.setValue(null);

            return 0x80004002;
        };
        Ref reference = self -> 1;
        callbacks = new Callback[]{query, reference, reference, invoke};

        for (int i = 0; i < callbacks.length; i++) {
            table.setPointer((long) i * Native.POINTER_SIZE, CallbackReference.getFunctionPointer(callbacks[i]));
        }

        pointer.setPointer(0, table);
    }
}
