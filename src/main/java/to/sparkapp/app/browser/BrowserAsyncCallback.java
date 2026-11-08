package to.sparkapp.app.browser;

import java.util.function.BiConsumer;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.win32.StdCallLibrary.StdCallCallback;

/** Completion handler whose Invoke receives an HRESULT and a result object or string. */
final class BrowserAsyncCallback extends BrowserComCallback {
    private interface Invoke extends StdCallCallback {
        int invoke(Pointer self, int result, Pointer value);
    }

    private static final Invoke INVOKE = (self, result, value) ->
            complete(self, BrowserAsyncCallback.class, callback -> callback.completion.accept(result, value));
    private static final Memory VTABLE = vtable(INVOKE);

    private final BiConsumer<Integer, Pointer> completion;

    BrowserAsyncCallback(String iid, BiConsumer<Integer, Pointer> completion) {
        super(iid, VTABLE);
        this.completion = completion;
    }

    static int retainedCount() {
        return retainedCount(BrowserAsyncCallback.class);
    }
}
