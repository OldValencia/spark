package to.sparkapp.app.browser;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import com.sun.jna.ptr.PointerByReference;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Tag("native")
@Timeout(60)
class InlineBrowserPagesTest {
    @Test
    void retainedAccountPageUsesTheOriginalViewportAndClosingItRestoresTheLiveParent() throws Exception {
        try (var server = new AuthenticationFixture(); var browser = new WindowsBrowserTest.Harness()) {
            BrowserAuthenticationTest.installPageScript(browser);
            enable(browser);
            browser.load(server.site + "/start");
            browser.eval("draft.value='parent-unsent'");
            browser.loads.clear();
            browser.eval("window.account=window.open('" + server.site + "/u/1/app?pageId=none','account')");
            assertEquals(Boolean.TRUE, browser.loads.poll(20, TimeUnit.SECONDS));
            assertEquals(server.site + "/u/1/app?pageId=none", call(browser, view -> view.windows().source()));
            var extraPage = call(browser, view -> view.windows().hasExtraPage());
            assertTrue(extraPage);
            var sameViewport = call(browser, view -> {
                var context = view.windows().activeContext();
                var parentHost = new PointerByReference();
                var childHost = new PointerByReference();
                ComCalls.check(ComCalls.call(context.getParent().getController(), 21, parentHost));
                ComCalls.check(ComCalls.call(context.getController(), 21, childHost));

                return parentHost.getValue().equals(childHost.getValue());
            });
            assertTrue(sameViewport, "Child contexts must use the same viewport HWND");
            System.out.println(new NativeDevTools(browser).request("Runtime.evaluate", """
                    {"expression":"JSON.stringify({probe:String(window.probe),spark:String(window.sparkCall),href:location.href})","returnByValue":true}
                    """));
            browser.eval("window.probe('child:'+Boolean(window.opener));draft.value='child-unsent'");
            assertEquals("child:true", browser.message());
            call(browser, view -> {
                view.windows().closeExtraPage();

                return null;
            });
            var stillExtra = call(browser, view -> view.windows().hasExtraPage());
            assertFalse(stillExtra);
            browser.eval("window.probe(draft.value+':'+window.account.closed)");
            assertEquals("parent-unsent:true", browser.message());
        }
    }

    static void enable(WindowsBrowserTest.Harness browser) throws Exception {
        call(browser, view -> {
            view.windows().enablePages("window.probe=value=>chrome.webview.postMessage(String(value));\n" + BrowserPageScripts.INIT_SCRIPT,
                    value -> {
                        if (!value.startsWith("[")) {
                            browser.messages.add(value);
                        }
                    });

            return null;
        });
    }

    static void awaitExtra(WindowsBrowserTest.Harness browser) throws Exception {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);

        while (System.nanoTime() < deadline) {
            if (!call(browser, view -> view.windows().hasExtraPage())) {
                return;
            }

            Thread.sleep(20);
        }

        fail("The expected browsing context did not become active");
    }

    static <T> T call(WindowsBrowserTest.Harness browser, Function<Webview, T> action) throws Exception {
        var result = new CompletableFuture<T>();
        browser.run(view -> {
            try {
                result.complete(action.apply(view));
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            }
        });

        return result.get(15, TimeUnit.SECONDS);
    }
}
