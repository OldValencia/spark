package to.sparkapp.app.browser;

import static org.junit.jupiter.api.Assertions.*;
import static to.sparkapp.app.browser.InlineBrowserPagesTest.call;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Tag("native")
@Timeout(60)
class BrowserMobileViewTest {
    @Test
    void theSiteAndPagesItOpensSeeAPhoneUntilTheDesktopVersionIsBack() throws Exception {
        var agentSeenByServer = new AtomicReference<String>();
        var server = pageServer(agentSeenByServer);
        var site = "http://127.0.0.1:" + server.getAddress().getPort();

        try (var browser = new WindowsBrowserTest.Harness()) {
            BrowserAuthenticationTest.installPageScript(browser);
            InlineBrowserPagesTest.enable(browser);
            browser.load(site + "/start");
            var page = new NativeDevTools(browser);

            assertFalse(agentSeenByServer.get().contains("Mobile"));

            switchTo(browser, true);

            assertTrue(agentSeenByServer.get().contains("Android"), "The site gets a phone's user agent");
            assertTrue(page.evaluate("navigator.userAgent").contains("Mobile"));
            assertEquals("true", page.evaluate("String(navigator.userAgentData.mobile)"), "Client hints say mobile");
            assertEquals("Android", page.evaluate("navigator.userAgentData.platform"));

            browser.loads.clear();
            browser.eval("window.open('" + site + "/opened','_blank')");
            assertEquals(Boolean.TRUE, browser.loads.poll(20, TimeUnit.SECONDS));
            boolean extraPage = call(browser, view -> view.windows().hasExtraPage());
            assertTrue(extraPage, "The site opens its page inside Spark");

            assertTrue(page.evaluate("navigator.userAgent").contains("Android"), "A page the site opens is mobile too");

            switchTo(browser, false);

            assertFalse(page.evaluate("navigator.userAgent").contains("Android"), "The desktop version comes back");
            assertEquals("false", page.evaluate("String(navigator.userAgentData.mobile)"));
            assertFalse(agentSeenByServer.get().contains("Android"));
        } finally {
            server.stop(0);
        }
    }

    private static void switchTo(WindowsBrowserTest.Harness browser, boolean mobile) throws Exception {
        browser.loads.clear();
        call(browser, view -> {
            view.windows().setMobile(mobile);
            view.windows().reloadPage();

            return null;
        });
        assertEquals(Boolean.TRUE, browser.loads.poll(20, TimeUnit.SECONDS), "The page loads again");
    }

    private static HttpServer pageServer(AtomicReference<String> agentSeenByServer) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            agentSeenByServer.set(String.valueOf(exchange.getRequestHeaders().getFirst("User-Agent")));
            var body = "<!doctype html><title>Version fixture</title>".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);

            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();

        return server;
    }
}
