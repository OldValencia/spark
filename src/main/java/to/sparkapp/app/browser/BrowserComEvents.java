package to.sparkapp.app.browser;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.sun.jna.Pointer;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.win32.StdCallLibrary.StdCallCallback;
import lombok.extern.slf4j.Slf4j;

import static to.sparkapp.app.browser.ComCalls.call;
import static to.sparkapp.app.browser.ComCalls.check;

/** Retains COM event handlers until their owning controller is closed. */
@Slf4j
final class BrowserComEvents {
    private interface Event extends StdCallCallback {
        int invoke(Pointer self, Pointer sender, Pointer args);
    }

    private final List<BrowserEventCallback> handlers = new ArrayList<>();
    private final List<Runnable> removals = new ArrayList<>();

    void listen(Pointer target, int add, int remove, String iid, Consumer<Pointer> listener) {
        Event invoke = (self, sender, args) -> {
            try {
                listener.accept(args);

                return 0;
            } catch (Throwable failure) {
                log.error("Browser event failed", failure);

                return 0x80004005;
            }
        };
        var handler = new BrowserEventCallback(iid, invoke);
        var token = new LongByReference();
        check(call(target, add, handler.pointer, token));
        handlers.add(handler);
        removals.add(() -> call(target, remove, token.getValue()));
    }

    void detach(boolean processExited) {
        if (!processExited) {
            removals.forEach(Runnable::run);
        }

        removals.clear();
    }

    int retainedCount() {
        return handlers.size();
    }
}
