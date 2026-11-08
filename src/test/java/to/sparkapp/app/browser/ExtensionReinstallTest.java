package to.sparkapp.app.browser;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import to.sparkapp.app.extensions.ExtensionBootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("native")
@Timeout(45)
class ExtensionReinstallTest {
    @TempDir
    Path directory;

    @Test
    void preparingAnExistingFolderPreservesExtensionIdentityAndLocalStorage() throws Exception {
        Files.writeString(directory.resolve("manifest.json"), """
                {"manifest_version":3,"name":"Reinstall fixture","version":"1.0",
                 "permissions":["storage"],"background":{"service_worker":"worker.js"}}
                """);
        Files.writeString(directory.resolve("worker.js"), "chrome.runtime.onMessage.addListener((message,sender,respond)=>respond(true));");
        Files.writeString(directory.resolve("popup.html"), "<!doctype html><body>Reinstall fixture</body>");
        try (var browser = new WindowsBrowserTest.Harness()) {
            var initial = BrowserExtensionsTest.call(browser, extensions -> extensions.install(directory));
            browser.load("chrome-extension://" + initial.id() + "/popup.html");
            ExtensionContextTest.evaluate(browser, "chrome.storage.local.set({sparkStorageFixture:'preserved'})");
            browser.load("about:blank");
            new ExtensionBootstrap().prepare(directory);
            var updated = BrowserExtensionsTest.call(browser, extensions -> extensions.install(directory));
            try {
                assertEquals(initial.id(), updated.id(), "Keeping the installed path must retain the unpacked extension ID");
                browser.load("chrome-extension://" + updated.id() + "/popup.html");
                assertEquals("preserved", ExtensionContextTest.evaluate(browser,
                        "chrome.storage.local.get('sparkStorageFixture')").path("sparkStorageFixture").asText());
            } finally {
                BrowserExtensionsTest.call(browser, extensions -> extensions.remove(updated.id()));
            }
        }
    }
}
