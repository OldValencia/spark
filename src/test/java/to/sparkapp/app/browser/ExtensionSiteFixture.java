package to.sparkapp.app.browser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.sun.jna.WString;

import static to.sparkapp.app.browser.ComCalls.*;

/** All website and mock advertising resources are local, including their host mappings. */
final class ExtensionSiteFixture {
    static final String URL = "https://spark-extension-test.invalid/index.html";

    static void install(WindowsBrowserTest.Harness browser, Path directory) throws Exception {
        Files.createDirectories(directory.resolve("pagead/js"));
        Files.writeString(directory.resolve("pagead/js/adsbygoogle.js"), "window.testAdLoaded=true;");
        Files.writeString(directory.resolve("index.html"), """
                <!doctype html><html><head><meta charset="UTF-8"><title>Extension website test</title>
                <style>body{background:white;color:black}</style></head><body>
                <h1>Local extension fixture</h1><input id="username"><input type="password">
                <div id="cookie-law-info-bar">This site uses cookies. Accept all cookies.</div>
                <script src="https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js"></script>
                </body></html>
                """);
        var ready = new CompletableFuture<Void>();
        browser.run(view -> {
            var core = query(view.windows().activeContext().getCore(), "a0d6df20-3b92-416d-aa0c-437a9c727857");
            try {
                for (var host : new String[]{"spark-extension-test.invalid", "pagead2.googlesyndication.com"}) {
                    check(call(core, 71, new WString(host), new WString(directory.toAbsolutePath().toString()), 1));
                }
                ready.complete(null);
            } catch (Exception error) {
                ready.completeExceptionally(error);
            } finally {
                release(core);
            }
        });
        ready.get(10, TimeUnit.SECONDS);
    }

    static String inspect(WindowsBrowserTest.Harness browser) throws Exception {
        return ExtensionSamplesTest.evaluate(browser, """
                JSON.stringify({adLoaded:!!window.testAdLoaded,
                  darkReaderStyles:document.querySelectorAll('.darkreader').length,
                  background:getComputedStyle(document.body).backgroundColor,
                  cookieDisplay:getComputedStyle(document.querySelector('#cookie-law-info-bar')).display})
                """);
    }
}
