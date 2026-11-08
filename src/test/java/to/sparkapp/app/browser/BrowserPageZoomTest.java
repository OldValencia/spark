package to.sparkapp.app.browser;

import static org.junit.jupiter.api.Assertions.*;
import static to.sparkapp.app.browser.InlineBrowserPagesTest.call;

import java.util.concurrent.TimeUnit;

import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Tag("native")
@Timeout(60)
class BrowserPageZoomTest {
    private static final double ZOOM = 1.44;

    @Test
    void siteZoomCoversNavigationAndPagesTheSiteOpens() throws Exception {
        try (var server = new AuthenticationFixture(); var browser = new WindowsBrowserTest.Harness()) {
            BrowserAuthenticationTest.installPageScript(browser);
            InlineBrowserPagesTest.enable(browser);
            browser.load(server.site + "/start");
            var baseRatio = pixelRatio(browser);

            assertFalse(builtInZoomEnabled(browser), "Spark steps the zoom; WebView2's own zoom must be off");

            setZoom(browser, ZOOM);
            awaitRatio(browser, baseRatio * ZOOM, "The page must render at the site zoom");

            browser.load(server.identity + "/arbitrary-company-route");
            awaitRatio(browser, baseRatio * ZOOM, "The site zoom must survive navigation to another origin");

            browser.loads.clear();
            browser.eval("window.open('" + server.site + "/arbitrary-company-route','_blank')");
            assertEquals(Boolean.TRUE, browser.loads.poll(20, TimeUnit.SECONDS));
            boolean extraPage = call(browser, view -> view.windows().hasExtraPage());
            assertTrue(extraPage, "The site must open its page inside Spark");
            assertFalse(builtInZoomEnabled(browser), "Pages opened by the site must not use WebView2's own zoom");
            awaitRatio(browser, baseRatio * ZOOM, "A page opened by the site must start at the site zoom");

            setZoom(browser, 1.0);
            awaitRatio(browser, baseRatio, "Changing the zoom must reach pages opened by the site");
        }
    }

    private static void setZoom(WindowsBrowserTest.Harness browser, double factor) throws Exception {
        call(browser, view -> {
            view.windows().setZoomFactor(factor);

            return null;
        });
    }

    private static boolean builtInZoomEnabled(WindowsBrowserTest.Harness browser) throws Exception {
        return call(browser, view -> {
            var settings = new PointerByReference();
            ComCalls.check(ComCalls.call(view.windows().activeContext().getCore(), 3, settings));

            try {
                var enabled = new IntByReference();
                ComCalls.check(ComCalls.call(settings.getValue(), 17, enabled));

                return enabled.getValue() != 0;
            } finally {
                ComCalls.release(settings.getValue());
            }
        });
    }

    private static double pixelRatio(WindowsBrowserTest.Harness browser) throws Exception {
        browser.eval("window.probe(String(devicePixelRatio))");

        return Double.parseDouble(browser.message());
    }

    /** The renderer applies a new zoom asynchronously. */
    private static void awaitRatio(WindowsBrowserTest.Harness browser, double expected, String failure)
            throws Exception {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        var actual = pixelRatio(browser);

        while (Math.abs(actual - expected) > 0.02 && System.nanoTime() < deadline) {
            Thread.sleep(50);
            actual = pixelRatio(browser);
        }

        assertEquals(expected, actual, 0.02, failure);
    }
}
