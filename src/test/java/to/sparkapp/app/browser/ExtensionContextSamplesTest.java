package to.sparkapp.app.browser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import to.sparkapp.app.extensions.ExtensionBootstrap;
import to.sparkapp.app.extensions.ExtensionImporter;
import to.sparkapp.app.extensions.ExtensionManifest;
import to.sparkapp.app.utils.NativeWindowUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("native")
@Timeout(100)
class ExtensionContextSamplesTest {
    @TempDir
    Path directory;

    @Test
    void preparedExtensionMenusIdentifyTheSelectedWebsite() throws Exception {
        var packages = Path.of("local", "extensions-samples");
        assumeTrue(Files.isRegularFile(packages.resolve("darkreader.crx")));
        var report = new LinkedHashMap<String, Object>();
        try (var site = new WindowsBrowserTest.Harness()) {
            ExtensionSiteFixture.install(site, directory.resolve("website"));
            site.load(ExtensionSiteFixture.URL);
            var windowId = ExtensionContextTest.windowId(site);
            for (var archive : new String[]{"darkreader.crx", "ublock.zip"}) {
                var source = new ExtensionImporter().importArchive(packages.resolve(archive), directory);
                var folder = directory.resolve("prepared-" + archive);
                new ExtensionBootstrap().prepareCopy(source, folder);
                var manifest = ExtensionManifest.read(folder);
                var extension = BrowserExtensionsTest.call(site, extensions -> extensions.install(folder));
                new CountDownLatch(1).await(2, TimeUnit.SECONDS);
                site.load(ExtensionSiteFixture.URL);
                var popup = new WindowsBrowserTest.Harness();
                try {
                    var url = "chrome-extension://" + extension.id() + "/" + manifest.popup();
                    popup.run(view -> {
                        ExtensionPopupNavigation.install(view);
                        view.setInitScript(ExtensionContextScript.create(extension.id(), windowId));
                        view.loadURL(url);
                        NativeWindowUtils.setVisible(view.getNativeWindowPointer(), true);
                    });
                    var text = awaitSiteName(popup);
                    report.put(archive, Map.of("text", text, "source", ExtensionContextTest.evaluate(popup, "location.href")));
                    assertTrue(text.contains("spark-extension-test.invalid"), manifest.name() + " does not show the selected website: " + text);
                    var tabs = ExtensionContextTest.evaluate(popup, "chrome.tabs.query({active:true,currentWindow:true,windowType:'normal'})");
                    assertEquals(ExtensionSiteFixture.URL, tabs.get(0).path("url").asText());
                    if (archive.equals("ublock.zip")) {
                        verifySiteSwitch(popup, extension.id());
                        report.put("ublockSiteSwitchVerified", true);
                    }
                } finally {
                    popup.close();
                    BrowserExtensionsTest.call(site, extensions -> extensions.remove(extension.id()));
                }
            }
        } finally {
            var path = Path.of("build", "reports", "extensions", "context-samples.json");
            Files.createDirectories(path.getParent());
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(path.toFile(), report);
        }
    }

    private static String awaitSiteName(WindowsBrowserTest.Harness popup) throws Exception {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        var text = "";
        while (System.nanoTime() < deadline && !text.contains("spark-extension-test.invalid")) {
            text = ExtensionContextTest.evaluate(popup, "document.body?.innerText || ''").asText();
            new CountDownLatch(1).await(200, TimeUnit.MILLISECONDS);
        }

        return text;
    }

    private static void verifySiteSwitch(WindowsBrowserTest.Harness popup, String extensionId) throws Exception {
        var url = new ObjectMapper().writeValueAsString(ExtensionSiteFixture.URL);
        var query = "import(chrome.runtime.getURL('js/background.js')).then(module=>module.default.getNetFilteringSwitch(" + url + "))";
        assertTrue(ExtensionBackgroundProbe.evaluate(popup, extensionId, query).asBoolean());
        ExtensionContextTest.evaluate(popup, "document.querySelector('#switch').click()");
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            if (!ExtensionBackgroundProbe.evaluate(popup, extensionId, query).asBoolean()) {
                return;
            }
            new CountDownLatch(1).await(100, TimeUnit.MILLISECONDS);
        }
        fail("The uBlock site switch did not affect the selected website");
    }
}
