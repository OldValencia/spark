package to.sparkapp.app.browser;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.win32.StdCallLibrary.StdCallCallback;

import static to.sparkapp.app.browser.ComCalls.call;
import static to.sparkapp.app.browser.ComCalls.check;

/**
 * Test-only protocol access; callbacks remain retained until the harness shuts down.
 */
final class NativeDevTools {
    private interface Completed extends StdCallCallback {
        int invoke(Pointer self, int status, Pointer json);
    }

    private final WindowsBrowserTest.Harness browser;
    private final List<BrowserEventCallback> callbacks = new ArrayList<>();

    NativeDevTools(WindowsBrowserTest.Harness browser) {
        this.browser = browser;
    }

    String request(String method, String parameters) throws Exception {
        var result = new CompletableFuture<String>();
        browser.run(view -> {
            try {
                var core = view.windows().activeContext().getCore();
                Completed completed = (self, status, json) -> {
                    if (status < 0) {
                        result.completeExceptionally(new IllegalStateException("Protocol error: " + status));
                    } else {
                        result.complete(json == null ? "{}" : json.getWideString(0));
                    }

                    return 0;
                };
                var callback = new BrowserEventCallback("5c4889f0-5ef6-4c5a-952c-d8f1b92d0574", completed);
                callbacks.add(callback);
                check(call(core, 36, new WString(method), new WString(parameters), callback.pointer));
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            }
        });

        return result.get(15, TimeUnit.SECONDS);
    }
}
