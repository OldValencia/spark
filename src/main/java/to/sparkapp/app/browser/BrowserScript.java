package to.sparkapp.app.browser;

import static to.sparkapp.app.browser.ComCalls.call;
import static to.sparkapp.app.browser.ComCalls.check;

import java.util.function.Consumer;

import com.sun.jna.Pointer;
import com.sun.jna.WString;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Runs a script in a page and returns its JSON result; must be called on the browser thread. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class BrowserScript {
    private static final int CORE_EXECUTE_SCRIPT = 29;
    private static final String EXECUTE_SCRIPT_COMPLETED = "49511172-cc67-4bca-9923-137112f4c4cc";

    /** The result receives the JSON-encoded value, or null when the script could not run. */
    static void execute(Pointer core, String script, Consumer<String> result) {
        try (var callback = new BrowserAsyncCallback(EXECUTE_SCRIPT_COMPLETED, (status, value) ->
                result.accept(status >= 0 && value != null ? value.getWideString(0) : null))) {
            check(call(core, CORE_EXECUTE_SCRIPT, new WString(script), callback.pointer()));
        }
    }
}
