package to.sparkapp.app.browser;

import static org.junit.jupiter.api.Assertions.*;
import static to.sparkapp.app.browser.InlineBrowserPagesTest.call;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Tag("native")
@Timeout(60)
class InlinePageNavigationTest {
    @Test
    void aPostIntoANewPageReturnsToTheSiteWithoutCopyingOrReissuingItsRequest() throws Exception {
        try (var server = new AuthenticationFixture(); var browser = new WindowsBrowserTest.Harness()) {
            BrowserAuthenticationTest.installPageScript(browser);
            InlineBrowserPagesTest.enable(browser);
            browser.load(server.site + "/start");
            browser.loads.clear();
            browser.eval("login.target='_blank';login.requestSubmit()");
            assertEquals(Boolean.TRUE, browser.loads.poll(20, TimeUnit.SECONDS));
            assertEquals("POST:nonce=local-fixture", server.submitted.get());
            assertEquals(server.site + "/custom-return?nieznany=temporary", call(browser, view -> view.windows().source()));
            var extra = call(browser, view -> view.windows().hasExtraPage());
            assertTrue(extra, "The returned site must remain visible in the existing Spark viewport");
            browser.eval("window.probe('returned:'+Boolean(opener))");
            assertEquals("returned:true", browser.message());
        }
    }

    @Test
    void nestedPagesAndBackgroundWorkKeepTheirLiveParentsAndReturnWithoutReloading() throws Exception {
        try (var server = new AuthenticationFixture(); var browser = new WindowsBrowserTest.Harness()) {
            BrowserAuthenticationTest.installPageScript(browser);
            InlineBrowserPagesTest.enable(browser);
            browser.load(server.site + "/start");
            open(browser, server.site + "/first-child");
            browser.eval("draft.value='first-child-unsent'");
            open(browser, server.identity + "/arbitrary-company-route");
            call(browser, view -> {
                view.windows().setPresentation(false, true);

                return null;
            });
            browser.eval("setTimeout(()=>window.probe('hidden:'+document.hidden),250)");
            assertEquals("hidden:true", browser.message());
            call(browser, view -> {
                view.windows().closeExtraPage();

                return null;
            });
            assertEquals(server.site + "/first-child", call(browser, view -> view.windows().source()));
            browser.eval("window.probe(draft.value)");
            assertEquals("first-child-unsent", browser.message());
            call(browser, view -> {
                view.windows().returnToRoot();

                return null;
            });
            assertEquals(server.site + "/start", call(browser, view -> view.windows().source()));
            assertEquals(0, BrowserAsyncCallback.retainedCount(), "Completed controller/script callbacks must be released");
        }
    }

    private static void open(WindowsBrowserTest.Harness browser, String url) throws Exception {
        browser.loads.clear();
        browser.eval("window.open('" + url + "','_blank')");
        assertEquals(Boolean.TRUE, browser.loads.poll(20, TimeUnit.SECONDS));
    }
}
