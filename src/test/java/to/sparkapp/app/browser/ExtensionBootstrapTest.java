package to.sparkapp.app.browser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import to.sparkapp.app.extensions.ExtensionBootstrap;

import static org.junit.jupiter.api.Assertions.*;

@Tag("native")
@Timeout(90)
class ExtensionBootstrapTest {
    @TempDir
    Path directory;

    /**
     * One site browser stays open for every form, as Spark's main browser does. Closing the last
     * browser and creating a new one at once can attach it to a WebView2 process that is still
     * shutting down, where newly installed service workers never start.
     */
    @Test
    void cachedNativeReferencesUseTheAdapterFromStartup() throws Exception {
        try (var site = new WindowsBrowserTest.Harness()) {
            ExtensionSiteFixture.install(site, directory.resolve("site"));
            site.load(ExtensionSiteFixture.URL);
            for (var kind : List.of("scripts", "page", "classic", "module")) {
                verify(site, kind);
            }
        }
    }

    private void verify(WindowsBrowserTest.Harness site, String kind) throws Exception {
        var source = Files.createDirectories(directory.resolve(kind));
        var manifest = new ObjectMapper().createObjectNode();
        manifest.put("manifest_version", kind.equals("scripts") || kind.equals("page") ? 2 : 3);
        manifest.put("name", "Startup adapter fixture");
        manifest.put("version", "1.0");
        manifest.putArray("permissions").add("tabs").add("storage");
        var background = manifest.putObject("background");
        if (kind.equals("scripts")) {
            background.putArray("scripts").add("worker.js");
        } else if (kind.equals("page")) {
            background.put("page", "background.html");
            Files.writeString(source.resolve("background.html"), "<!doctype html><html><head></head><body><script src='worker.js'></script></body></html>");
        } else {
            background.put("service_worker", "worker.js");
            if (kind.equals("module")) {
                background.put("type", "module");
            }
        }
        new ObjectMapper().writeValue(source.resolve("manifest.json").toFile(), manifest);
        var script = """
                /* License fixture */
                'use strict'
                const api=globalThis.browser?.tabs ? globalThis.browser : chrome;
                const cachedQuery=api.tabs.query;
                const strict=(function(){return this})()===undefined;
                chrome.runtime.onMessage.addListener((message,sender,respond)=>{
                    const criteria={active:true,currentWindow:true,windowType:'normal'};
                    if(api===globalThis.browser){
                        cachedQuery.call(api.tabs,criteria).then(tabs=>respond({tabs,strict}));
                    }else{
                        cachedQuery.call(api.tabs,criteria,tabs=>respond({tabs,strict}));
                    }
                    return true;
                });
                """;
        Files.writeString(source.resolve("worker.js"), script);
        Files.writeString(source.resolve("popup.html"), "<!doctype html><body>Startup fixture</body>");
        var prepared = directory.resolve("prepared-" + kind);
        new ExtensionBootstrap().prepareCopy(source, prepared);
        assertEquals(script, Files.readString(source.resolve("worker.js")), "Source packages must remain unchanged");
        var installed = BrowserExtensionsTest.call(site, extensions -> extensions.install(prepared));
        var windowId = ExtensionContextTest.windowId(site);
        try (var popup = ExtensionContextTest.openPopup(installed.id(), windowId)) {
            var response = ExtensionContextTest.evaluate(popup, "chrome.runtime.sendMessage({snapshot:true})");
            assertTrue(response.path("strict").asBoolean(), kind + ": the worker's directive prologue must be preserved");
            assertEquals(ExtensionSiteFixture.URL, response.path("tabs").get(0).path("url").asText(), kind);
        } finally {
            BrowserExtensionsTest.call(site, extensions -> extensions.remove(installed.id()));
        }
    }
}
