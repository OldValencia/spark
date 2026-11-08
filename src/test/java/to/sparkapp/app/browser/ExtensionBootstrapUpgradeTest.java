package to.sparkapp.app.browser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.extensions.ExtensionEntry;
import to.sparkapp.app.extensions.ExtensionManager;
import to.sparkapp.app.extensions.ExtensionStore;
import to.sparkapp.app.support.JavaFxTestRuntime;

/**
 * Reproduces an installation prepared by an earlier build: its bootstrap must be replaced and the
 * extension reloaded, otherwise the real popup host cannot pass the selected site to background code.
 */
@Tag("native")
@Timeout(90)
class ExtensionBootstrapUpgradeTest {
    private static final String BOOTSTRAP = "spark-context-bootstrap.js";
    private static final String MARKER = ".spark-bootstrap.json";
    private static final String SNAPSHOT = "chrome.runtime.sendMessage({snapshot:true})";

    @Test
    void anOutdatedBootstrapIsReplacedAndTheRealPopupPassesTheSelectedSite() throws Exception {
        JavaFxTestRuntime.start();
        var folder = fixture(new ExtensionStore().root());
        var browser = new WebviewManager(new AppPreferences());
        var manager = new ExtensionManager();
        browser.init("about:blank", 0, 0, 0, 600, 400);
        var owner = JavaFxTestRuntime.call(ExtensionBootstrapUpgradeTest::owner);
        try {
            var entry = find(manager.refresh(browser).get(25, TimeUnit.SECONDS), folder);
            installPreviousBootstrap(folder);
            entry = find(manager.refresh(browser).get(25, TimeUnit.SECONDS), folder);
            assertEquals(Files.readString(folder.resolve(BOOTSTRAP)), ExtensionContextScript.bootstrap(),
                    "Synchronization must replace a bootstrap written by an earlier build");

            var windowId = browser.extensionContextWindow().get(5, TimeUnit.SECONDS);
            var popup = open(owner, entry, windowId);
            try {
                var tabs = evaluate(popup, SNAPSHOT);
                assertEquals(1, tabs.size(), "The background worker must see the selected site: " + tabs);
                assertEquals(windowId, tabs.get(0).path("windowId").asInt());
            } finally {
                JavaFxTestRuntime.call(() -> {
                    popup.getWindow().close();
                    return null;
                });
            }
            manager.remove(browser, entry).get(25, TimeUnit.SECONDS);
        } finally {
            JavaFxTestRuntime.call(() -> {
                owner.close();
                return null;
            });
            var stopped = new CompletableFuture<Void>();
            browser.shutdown(() -> stopped.complete(null));
            stopped.get(10, TimeUnit.SECONDS);
            delete(folder);
        }
    }

    private static Path fixture(Path root) throws Exception {
        var folder = Files.createTempDirectory(root, "upgrade-test-");
        Files.writeString(folder.resolve("manifest.json"), """
                {"manifest_version":3,"name":"Bootstrap upgrade fixture","version":"1.0","permissions":["tabs"],
                 "background":{"service_worker":"worker.js"},"action":{"default_popup":"popup.html"}}
                """);
        Files.writeString(folder.resolve("popup.html"), "<!doctype html><body style='width:200px;height:100px'>Fixture</body>");
        Files.writeString(folder.resolve("worker.js"), """
                chrome.runtime.onMessage.addListener((message,sender,respond)=>{
                    chrome.tabs.query({active:true,lastFocusedWindow:true,windowType:'normal'}).then(respond);
                    return true;
                });
                """);
        return folder;
    }

    /** Writes the bootstrap an earlier build produced and records it as prepared, like an existing installation. */
    private static void installPreviousBootstrap(Path folder) throws Exception {
        String previous;
        try (var stream = ExtensionBootstrapUpgradeTest.class.getResourceAsStream("/js/extension-context-previous.js")) {
            assertNotNull(stream);
            previous = new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("__SPARK_EXTENSION_ID__", "chrome.runtime.id")
                    .replace("__SPARK_WINDOW_ID__", "null")
                    .replace("__SPARK_BOOTSTRAP__", "true");
        }
        Files.writeString(folder.resolve(BOOTSTRAP), previous);

        var json = new ObjectMapper();
        var marker = (ObjectNode) json.readTree(folder.resolve(MARKER).toFile());
        var digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(folder.resolve(BOOTSTRAP)));
        ((ObjectNode) marker.path("files")).put(BOOTSTRAP, HexFormat.of().formatHex(digest));
        json.writeValue(folder.resolve(MARKER).toFile(), marker);
    }

    private static ExtensionPopup open(Stage owner, ExtensionEntry entry, int windowId) throws Exception {
        var popup = JavaFxTestRuntime.call(() -> {
            var created = new ExtensionPopup();
            created.open(owner, entry.manifest().name(), entry.id(), entry.manifest().popup(), windowId);
            return created;
        });
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            if (popup.getBrowser() != null && evaluate(popup, "location.protocol + document.readyState").asText().equals("chrome-extension:complete")) {
                return popup;
            }
            new CountDownLatch(1).await(50, TimeUnit.MILLISECONDS);
        }
        throw new AssertionError("The extension popup did not load");
    }

    private static JsonNode evaluate(ExtensionPopup popup, String expression) throws Exception {
        var view = popup.getBrowser();
        var result = new CompletableFuture<JsonNode>();
        var parameters = Map.of("expression", expression, "awaitPromise", true, "returnByValue", true);
        view.dispatch(() -> new BrowserDevTools(view.windows().activeContext().getCore())
                .send("Runtime.evaluate", parameters)
                .whenComplete((value, error) -> {
                    if (error == null) {
                        result.complete(value);
                    } else {
                        result.completeExceptionally(error);
                    }
                }));
        var value = result.get(10, TimeUnit.SECONDS);
        assertFalse(value.has("exceptionDetails"), value.toString());
        return value.path("result").path("value");
    }

    private static ExtensionEntry find(Iterable<ExtensionEntry> entries, Path folder) {
        for (var entry : entries) {
            if (entry.manifest().directory().equals(folder)) {
                assertNotNull(entry.id(), entry.error());
                return entry;
            }
        }
        throw new AssertionError("The fixture was not synchronized");
    }

    private static Stage owner() {
        var stage = new Stage();
        stage.setScene(new Scene(new StackPane(), 600, 500));
        stage.setX(-20000);
        stage.setY(-20000);
        stage.show();
        return stage;
    }

    private static void delete(Path folder) throws Exception {
        if (!Files.exists(folder)) {
            return;
        }
        try (var files = Files.walk(folder)) {
            for (var path : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
