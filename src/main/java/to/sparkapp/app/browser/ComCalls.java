package to.sparkapp.app.browser;

import com.sun.jna.Function;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Guid.GUID;
import com.sun.jna.ptr.PointerByReference;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ComCalls {
    static Pointer query(Pointer object, String iid) {
        var out = new PointerByReference();
        check(call(object, 0, new GUID(iid), out));

        return out.getValue();
    }

    static void release(Pointer object) {
        if (object != null) {
            call(object, 2);
        }
    }

    static void check(int result) {
        if (result < 0) {
            throw new IllegalStateException("WebView2 operation failed: 0x" + Integer.toHexString(result));
        }
    }

    static int call(Pointer object, int slot, Object... arguments) {
        var args = new Object[arguments.length + 1];
        args[0] = object;
        System.arraycopy(arguments, 0, args, 1, arguments.length);

        var function = object.getPointer(0).getPointer((long) slot * Native.POINTER_SIZE);

        return Function.getFunction(function, Function.ALT_CONVENTION).invokeInt(args);
    }
}
