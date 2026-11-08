package to.sparkapp.app.browser;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Tag("native")
@Timeout(70)
class BrowserExtensionsTest {
    @TempDir
    Path directory;

    @Test
    void installsListsOpensDisablesAndRemovesAnExtension() throws Exception {
        Files.writeString(directory.resolve("manifest.json"), """
                {"manifest_version":3,"name":"Spark native extension test","version":"1.0",
                 "permissions":["storage"],"action":{"default_popup":"popup.html"},
                 "options_ui":{"page":"options.html","open_in_tab":true}}
                """);
        Files.writeString(directory.resolve("popup.html"), "<html><body>Popup</body></html>");
        Files.writeString(directory.resolve("options.html"), "<html><body>Options</body></html>");
        try (var browser = new WindowsBrowserTest.Harness()) {
            var extension = call(browser, extensions -> extensions.install(directory));
            assertEquals("Spark native extension test", extension.name());
            assertTrue(extension.enabled());
            assertTrue(call(browser, BrowserExtensions::list).stream().anyMatch(item -> item.id().equals(extension.id())));
            browser.load("chrome-extension://" + extension.id() + "/popup.html");
            browser.eval("window.probe(chrome.runtime.id+':'+typeof chrome.storage.local.get)");
            assertEquals(extension.id() + ":function", browser.message());
            browser.load("chrome-extension://" + extension.id() + "/options.html");
            browser.eval("window.probe(document.body.innerText)");
            assertEquals("Options", browser.message());
            browser.load("about:blank");
            call(browser, extensions -> extensions.setEnabled(extension.id(), false));
            assertFalse(call(browser, BrowserExtensions::list).stream().filter(item -> item.id().equals(extension.id()))
                    .findFirst().orElseThrow().enabled());
            call(browser, extensions -> extensions.setEnabled(extension.id(), true));
            call(browser, extensions -> extensions.remove(extension.id()));
            assertTrue(call(browser, BrowserExtensions::list).stream().noneMatch(item -> item.id().equals(extension.id())));
        }
    }

    static <T> T call(WindowsBrowserTest.Harness browser,
                      Function<BrowserExtensions, CompletableFuture<T>> action) throws Exception {
        var result = new CompletableFuture<T>();
        browser.run(view -> {
            try {
                action.apply(new BrowserExtensions(view.windows().activeContext().getCore())).whenComplete((value, error) -> {
                    if (error == null) {
                        result.complete(value);
                    } else {
                        result.completeExceptionally(error);
                    }
                });
            } catch (Exception error) {
                result.completeExceptionally(error);
            }
        });

        return result.get(25, TimeUnit.SECONDS);
    }
}
