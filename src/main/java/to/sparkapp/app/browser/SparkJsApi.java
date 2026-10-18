package to.sparkapp.app.browser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import javafx.application.Platform;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Slf4j
class SparkJsApi {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final Map<String, Consumer<ArrayNode>> handlers = new ConcurrentHashMap<>();

    SparkJsApi(Webview webview) {
        webview.bind("sparkCall", (String rawArgs) -> {
            try {
                if (rawArgs == null || rawArgs.isBlank()) {
                    return null;
                }

                var root = MAPPER.readTree(rawArgs);
                if (!root.isArray() || root.isEmpty()) {
                    return null;
                }

                var command = root.get(0).asText();
                var handler = handlers.get(command);

                if (handler != null) {
                    var payload = MAPPER.createArrayNode();
                    for (int i = 1; i < root.size(); i++) {
                        payload.add(root.get(i));
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

    void on(String command, Consumer<ArrayNode> action) {
        handlers.put(command, action);
    }
}
