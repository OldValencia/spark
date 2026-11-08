package to.sparkapp.app.browser;

import static to.sparkapp.app.browser.ComCalls.call;
import static to.sparkapp.app.browser.ComCalls.check;
import static to.sparkapp.app.browser.ComCalls.query;
import static to.sparkapp.app.browser.ComCalls.release;

import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import lombok.RequiredArgsConstructor;

/** CDP commands stay on the owning WebView thread and do not open a debugging port. */
@RequiredArgsConstructor
final class BrowserDevTools {
    private static final int CORE_CALL_METHOD = 36;
    private static final int CORE11_CALL_SESSION_METHOD = 99;
    private static final String CORE11 = "0be78e56-c193-4051-b943-23b460c08bdb";
    private static final String METHOD_COMPLETED = "5c4889f0-5ef6-4c5a-952c-d8f1b92d0574";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Pointer core;

    CompletableFuture<JsonNode> send(String method, Object parameters) {
        return send(null, method, parameters);
    }

    /** A null session targets the page itself; otherwise the command goes to an attached target. */
    CompletableFuture<JsonNode> send(String session, String method, Object parameters) {
        var result = new CompletableFuture<JsonNode>();
        try (var callback = new BrowserAsyncCallback(METHOD_COMPLETED, (status, value) -> {
            try {
                check(status);
                result.complete(JSON.readTree(value.getWideString(0)));
            } catch (Exception error) {
                result.completeExceptionally(error);
            }
        })) {
            var serialized = new WString(JSON.writeValueAsString(parameters));
            if (session == null) {
                check(call(core, CORE_CALL_METHOD, new WString(method), serialized, callback.pointer()));
            } else {
                sendToSession(session, method, serialized, callback.pointer());
            }
        } catch (Exception error) {
            result.completeExceptionally(error);
        }

        return result;
    }

    private void sendToSession(String session, String method, WString parameters, Pointer callback) {
        var core11 = query(core, CORE11);
        try {
            check(call(core11, CORE11_CALL_SESSION_METHOD, new WString(session), new WString(method), parameters, callback));
        } finally {
            release(core11);
        }
    }
}
