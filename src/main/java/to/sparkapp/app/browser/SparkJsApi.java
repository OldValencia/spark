package to.sparkapp.app.browser;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import javafx.application.Platform;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Slf4j
class SparkJsApi {

    private final Webview webview;
    private final Map<String, Consumer<JsonArray>> handlers = new ConcurrentHashMap<>();
    private final Gson gson = new Gson();

    SparkJsApi(Webview webview) {
        this.webview = webview;

        this.webview.bind("sparkCall", (String rawArgs) -> {
            try {
                if (rawArgs == null || rawArgs.isBlank()) {
                    return null;
                }

                var args = JsonParser.parseString(rawArgs).getAsJsonArray();
                if (args.isEmpty()) {
                    return null;
                }

                var command = args.get(0).getAsString();
                var handler = handlers.get(command);

                if (handler != null) {
                    var payload = new JsonArray();
                    for (int i = 1; i < args.size(); i++) {
                        payload.add(args.get(i));
                    }

                    Platform.runLater(() -> handler.accept(payload));
                } else {
                    log.warn("SparkJsApi: Unknown command received from JS: {}", command);
                }
            } catch (Exception e) {
                log.error("SparkJsApi: Error processing JS call", e);
            }
            return null;
        });
    }

    void on(String command, Consumer<JsonArray> action) {
        handlers.put(command, action);
    }

    void emitToJs(String eventName, Object data) {
        if (webview == null) {
            return;
        }

        var jsonPayload = gson.toJson(data);
        var js = String.format(
                "window.dispatchEvent(new CustomEvent('%s', { detail: %s }));",
                eventName,
                jsonPayload
        );

        webview.eval(js);
    }
}
