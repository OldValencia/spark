package to.sparkapp.app.browser;

import java.util.function.Consumer;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.win32.StdCallLibrary.StdCallCallback;

/** Completion handler whose Invoke receives only an HRESULT, reported as success or failure. */
final class BrowserStatusCallback extends BrowserComCallback {
    private interface Invoke extends StdCallCallback {
        int invoke(Pointer self, int result);
    }

    private static final Invoke INVOKE = (self, result) ->
            complete(self, BrowserStatusCallback.class, callback -> callback.completion.accept(result >= 0));
    private static final Memory VTABLE = vtable(INVOKE);

    private final Consumer<Boolean> completion;

    BrowserStatusCallback(String iid, Consumer<Boolean> completion) {
        super(iid, VTABLE);
        this.completion = completion;
    }

    static int retainedCount() {
        return retainedCount(BrowserStatusCallback.class);
    }
}
