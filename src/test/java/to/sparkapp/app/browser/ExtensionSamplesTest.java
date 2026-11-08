package to.sparkapp.app.browser;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;

import com.sun.jna.WString;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import to.sparkapp.app.extensions.ExtensionImporter;
import to.sparkapp.app.extensions.ExtensionManifest;

@Tag("native")
@Timeout(150)
class ExtensionSamplesTest {
    @TempDir
    Path directory;

    @Test
    void probesOfficialPackagesWithoutAccountsOrExtensionSpecificAdapters() throws Exception {
        var packages = Path.of("local", "extensions-samples");
        assumeTrue(Files.isRegularFile(packages.resolve("bitwarden.zip")), "Optional official packages are not downloaded");
        var results = new LinkedHashMap<String, Map<String, Object>>();
        try (var browser = new WindowsBrowserTest.Harness()) {
            ExtensionSiteFixture.install(browser, directory.resolve("website"));
            for (var file : new String[]{"bitwarden.zip", "ublock.zip", "cookies.crx", "darkreader.crx"}) {
                var folder = new ExtensionImporter().importArchive(packages.resolve(file), directory);
                var manifest = ExtensionManifest.read(folder);
                var result = new LinkedHashMap<String, Object>();
                result.put("name", manifest.name());
                result.put("version", manifest.version());
                result.put("popup", manifest.popup());
                result.put("options", manifest.options());
                results.put(file, result);
                try {
                    var extension = BrowserExtensionsTest.call(browser, extensions -> extensions.install(folder));
                    result.put("installed", true);
                    browser.load(ExtensionSiteFixture.URL);
                    new CountDownLatch(1).await(3, TimeUnit.SECONDS);
                    result.put("websiteProbe", ExtensionSiteFixture.inspect(browser));
                    if (!manifest.popup().isBlank()) {
                        browser.load("chrome-extension://" + extension.id() + "/" + manifest.popup());
                        new CountDownLatch(1).await(3, TimeUnit.SECONDS);
                        var text = evaluate(browser, "JSON.stringify({id:globalThis.chrome?.runtime?.id,url:location.href,text:document.body.innerText.slice(0,1000)})");
                        result.put("popupProbe", text);
                        result.put("popupRuntimeVerified", text.contains(extension.id()));
                        evaluate(browser, ExtensionPopupSize.SCRIPT);
                        new CountDownLatch(1).await(300, TimeUnit.MILLISECONDS);
                        result.put("popupSize", evaluate(browser, ExtensionPopupSize.POLL_SCRIPT));
                        result.put("popover", ExtensionPopupPreview.capture(manifest.name(), extension.id(), manifest.popup(), file));
                    }
                    if (!manifest.options().isBlank()) {
                        browser.load("chrome-extension://" + extension.id() + "/" + manifest.options());
                        result.put("optionsOpened", true);
                    }
                    browser.load("about:blank");
                    BrowserExtensionsTest.call(browser, extensions -> extensions.remove(extension.id()));
                } catch (Exception error) {
                    result.put("error", error.toString());
                }
            }
        }
        var report = Path.of("build", "reports", "extensions", "samples.json");
        Files.createDirectories(report.getParent());
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(report.toFile(), results);
        System.out.println(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(results));
        assertEquals(4, results.size());
        assertTrue(results.values().stream().allMatch(result -> Boolean.TRUE.equals(result.get("installed"))),
                "Every requested sample must install; see build/reports/extensions/samples.json");
    }

    static String evaluate(WindowsBrowserTest.Harness browser, String script) throws Exception {
        var result = new CompletableFuture<String>();
        browser.run(view -> {
            try (var callback = new BrowserAsyncCallback("49511172-cc67-4bca-9923-137112f4c4cc", (status, value) -> {
                if (status < 0) {
                    result.completeExceptionally(new IllegalStateException("Script failed: " + Integer.toHexString(status)));
                } else {
                    result.complete(value == null ? "null" : value.getWideString(0));
                }
            })) {
                ComCalls.check(ComCalls.call(view.windows().activeContext().getCore(), 29, new WString(script), callback.pointer()));
            } catch (Exception error) {
                result.completeExceptionally(error);
            }
        });
        return result.get(20, TimeUnit.SECONDS);
    }
}
