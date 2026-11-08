package to.sparkapp.app.browser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import to.sparkapp.app.extensions.ExtensionBootstrap;

@Tag("native")
@Timeout(80)
class ExtensionContextTest {
    static final String RELEASE = "globalThis[Symbol.for('spark.extensionContext')]?.release?.()";
    private static final String SNAPSHOT = "chrome.runtime.sendMessage({snapshot:true})";

    @TempDir
    Path directory;

    @Test
    void popupAndPreparedWorkerUseTheSelectedSiteWithoutDebuggingSessions() throws Exception {
        var extensionFolder = preparedFixture();
        try (var site = new WindowsBrowserTest.Harness(); var otherSite = new WindowsBrowserTest.Harness()) {
            ExtensionSiteFixture.install(site, directory.resolve("website"));
            ExtensionSiteFixture.install(otherSite, directory.resolve("other-website"));
            site.load(ExtensionSiteFixture.URL);
            otherSite.load(ExtensionSiteFixture.URL);
            var extension = BrowserExtensionsTest.call(site, extensions -> extensions.install(extensionFolder));
            var windowId = windowId(site);
            try {
                int tabId;
                try (var popup = openPopup(extension.id(), windowId)) {
                    tabId = verifyPopupQueries(popup, windowId);
                    verifyWorkerFollowsTheSite(popup, site, tabId);
                    restartWorker(popup, extension.id());
                    assertEquals(tabId, evaluate(popup, SNAPSHOT).get(0).path("id").asInt(),
                            "The first request of a restarted worker must already use the selected site");
                    evaluate(popup, RELEASE);
                    assertEquals(0, evaluate(popup, SNAPSHOT).size(), "Closing must restore native worker queries");
                }

                var otherWindow = windowId(otherSite);
                try (var popup = openPopup(extension.id(), otherWindow)) {
                    var switched = evaluate(popup, SNAPSHOT).get(0);
                    assertEquals(otherWindow, switched.path("windowId").asInt());
                    assertNotEquals(tabId, switched.path("id").asInt(), "Identical site URLs must not confuse real tab identity");
                }
            } finally {
                BrowserExtensionsTest.call(site, extensions -> extensions.remove(extension.id()));
            }
        }
    }

    /** Opens the extension's page the way Spark's popup host does: one document init script, no CDP. */
    static WindowsBrowserTest.Harness openPopup(String extensionId, int windowId) throws Exception {
        var popup = new WindowsBrowserTest.Harness();
        popup.run(view -> view.setInitScript(ExtensionContextScript.create(extensionId, windowId)));
        popup.load("chrome-extension://" + extensionId + "/popup.html");
        return popup;
    }

    static int windowId(WindowsBrowserTest.Harness site) throws Exception {
        return command(site, "Browser.getWindowForTarget", Map.of()).path("windowId").asInt();
    }

    private int verifyPopupQueries(WindowsBrowserTest.Harness popup, int windowId) throws Exception {
        var selected = evaluate(popup, "chrome.tabs.query({active:true,currentWindow:true})");
        assertEquals(ExtensionSiteFixture.URL, selected.get(0).path("url").asText());
        assertEquals(windowId, selected.get(0).path("windowId").asInt());
        var tabId = selected.get(0).path("id").asInt();

        assertEquals("normal", evaluate(popup, "chrome.windows.getCurrent()").path("type").asText());
        assertEquals(windowId, evaluate(popup, "chrome.windows.getLastFocused()").path("id").asInt());
        assertEquals(1, evaluate(popup, "chrome.windows.getAll({windowTypes:['normal']})").size());
        var callbacks = evaluate(popup, "new Promise(resolve=>chrome.tabs.query("
                + "{active:true,lastFocusedWindow:true,windowType:'normal'},resolve))");
        assertEquals(tabId, callbacks.get(0).path("id").asInt());

        return tabId;
    }

    private void verifyWorkerFollowsTheSite(WindowsBrowserTest.Harness popup, WindowsBrowserTest.Harness site,
                                            int tabId) throws Exception {
        assertEquals(tabId, evaluate(popup, SNAPSHOT).get(0).path("id").asInt());
        evaluate(popup, "chrome.scripting.executeScript({target:{tabId:" + tabId
                + "},func:()=>document.querySelector('#username').value='context fixture'})");
        assertEquals("\"context fixture\"", ExtensionSamplesTest.evaluate(site, "document.querySelector('#username').value"));

        site.load(ExtensionSiteFixture.URL + "?navigated=1");
        assertEquals(ExtensionSiteFixture.URL + "?navigated=1",
                evaluate(popup, "chrome.tabs.query({active:true,currentWindow:true})").get(0).path("url").asText());
    }

    private void restartWorker(WindowsBrowserTest.Harness popup, String extensionId) throws Exception {
        String worker = null;
        for (var target : command(popup, "Target.getTargets", Map.of()).path("targetInfos")) {
            if (target.path("url").asText().equals("chrome-extension://" + extensionId + "/worker.js")) {
                worker = target.path("targetId").asText();
            }
        }
        assertNotNull(worker);
        command(popup, "Target.closeTarget", Map.of("targetId", worker));
    }

    private Path preparedFixture() throws Exception {
        var source = Files.createDirectories(directory.resolve("source"));
        Files.writeString(source.resolve("manifest.json"), """
                {"manifest_version":3,"name":"Context fixture","version":"1.0",
                "permissions":["tabs","scripting"],"host_permissions":["<all_urls>"],
                "background":{"service_worker":"worker.js"},"action":{"default_popup":"popup.html"}}
                """);
        Files.writeString(source.resolve("popup.html"), "<!doctype html><body>Context fixture</body>");
        Files.writeString(source.resolve("worker.js"), """
                chrome.runtime.onMessage.addListener((message,sender,respond)=>{
                    chrome.tabs.query({active:true,lastFocusedWindow:true,windowType:'normal'}).then(respond);
                    return true;
                });
                """);
        var prepared = directory.resolve("prepared");
        new ExtensionBootstrap().prepareCopy(source, prepared);
        return prepared;
    }

    static JsonNode evaluate(WindowsBrowserTest.Harness browser, String expression) throws Exception {
        var parameters = Map.of("expression", expression, "awaitPromise", true, "returnByValue", true);
        var result = command(browser, "Runtime.evaluate", parameters);
        assertFalse(result.has("exceptionDetails"), result.toString());
        return result.path("result").path("value");
    }

    static JsonNode command(WindowsBrowserTest.Harness browser, String method, Object parameters) throws Exception {
        var result = new CompletableFuture<JsonNode>();
        browser.run(view -> new BrowserDevTools(view.windows().activeContext().getCore()).send(method, parameters)
                .whenComplete((value, error) -> {
                    if (error == null) {
                        result.complete(value);
                    } else {
                        result.completeExceptionally(error);
                    }
                }));
        return result.get(10, TimeUnit.SECONDS);
    }
}
