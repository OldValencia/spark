package to.sparkapp.app.browser;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Tag("native")
@Timeout(60)
class BrowserAuthenticationTest {
    @Test
    void nativePopupsKeepOpenerProfileAndDraftAndFormsRetainTheirPostBody() throws Exception {
        try (var server = new AuthenticationFixture(); var browser = new WindowsBrowserTest.Harness()) {
            installPageScript(browser);
            InlineBrowserPagesTest.enable(browser);
            browser.load(server.site + "/start");
            for (var open : new String[]{
                    "window.loginPopup=window.open('" + server.identity + "/popup','signin','width=480,height=500')",
                    "window.loginPopup=window.open('about:blank','signin','width=480,height=500');" +
                            "setTimeout(()=>loginPopup.location='" + server.identity + "/popup',0)"}) {
                browser.eval(open);
                assertEquals("popup:true:unsent", browser.message());
                InlineBrowserPagesTest.awaitExtra(browser, false);
                browser.eval("(()=>{const closing=setInterval(()=>{if(loginPopup.closed){clearInterval(closing);window.probe('closed:true')}},20)})()");
                assertEquals("closed:true", browser.message());
            }
            browser.eval("document.querySelector('#separate').click()");
            assertEquals("popup:true:unsent", browser.message(), "target=_blank must remain a separate browsing context");
            InlineBrowserPagesTest.awaitExtra(browser, false);
            browser.loads.clear();
            browser.eval("document.querySelector('#login').requestSubmit()");
            assertEquals(Boolean.TRUE, browser.loads.poll(20, TimeUnit.SECONDS));
            assertEquals("POST:nonce=local-fixture", server.submitted.get());
            browser.eval("window.probe(location.href)");
            assertEquals(server.site + "/custom-return?nieznany=temporary", browser.message());
        }
    }

    static void installPageScript(WindowsBrowserTest.Harness browser) throws Exception {
        var installed = new CompletableFuture<Void>();
        browser.run(view -> {
            try {
                view.bind("sparkCall", args -> null);
                view.setInitScript(BrowserPageScripts.INIT_SCRIPT);
                installed.complete(null);
            } catch (Throwable failure) {
                installed.completeExceptionally(failure);
            }
        });
        installed.get(10, TimeUnit.SECONDS);
    }
}
