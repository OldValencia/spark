package to.sparkapp.app.browser;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;

final class ExtensionBackgroundProbe {
    static JsonNode evaluate(WindowsBrowserTest.Harness browser, String extensionId, String expression) throws Exception {
        var result = new CompletableFuture<JsonNode>();
        browser.run(view -> {
            var protocol = new BrowserDevTools(view.windows().activeContext().getCore());
            protocol.send("Target.getTargets", Map.of()).thenCompose(targets -> {
                for (var target : targets.path("targetInfos")) {
                    var type = target.path("type").asText();
                    if (target.path("url").asText().startsWith("chrome-extension://" + extensionId + "/")
                            && (type.equals("service_worker") || type.equals("background_page"))) {
                        return protocol.send("Target.attachToTarget", Map.of("targetId", target.path("targetId").asText(), "flatten", true))
                                .thenCompose(attached -> evaluate(protocol, attached.path("sessionId").asText(), expression));
                    }
                }
                return CompletableFuture.<JsonNode>failedFuture(new IllegalStateException("The extension has no active background context"));
            }).whenComplete((value, error) -> {
                if (error == null) {
                    result.complete(value);
                } else {
                    result.completeExceptionally(error);
                }
            });
        });
        return result.get(10, TimeUnit.SECONDS);
    }

    private static CompletableFuture<JsonNode> evaluate(BrowserDevTools protocol, String session, String expression) {
        return protocol.send(session, "Runtime.evaluate", Map.of("expression", expression, "awaitPromise", true, "returnByValue", true))
                .handle(Outcome::new).thenCompose(outcome -> protocol.send("Target.detachFromTarget", Map.of("sessionId", session))
                        .handle((ignored, error) -> {
                            if (outcome.error() != null) {
                                throw new IllegalStateException("The background probe failed", outcome.error());
                            }
                            if (outcome.value().has("exceptionDetails")) {
                                throw new IllegalStateException(outcome.value().toString());
                            }
                            return outcome.value().path("result").path("value");
                        }));
    }

    private record Outcome(JsonNode value, Throwable error) {
    }
}
