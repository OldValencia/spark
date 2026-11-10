package to.sparkapp.app.browser;

import static org.junit.jupiter.api.Assertions.*;
import static to.sparkapp.app.browser.InlineBrowserPagesTest.call;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.sun.jna.ptr.IntByReference;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Tag("native")
@Timeout(60)
class BrowserAudioTest {
    private static final String CORE_8 = "e9632730-6e1e-43ab-b7b8-7b2c9e62e094";
    private static final int GET_IS_MUTED = 83;

    @Test
    void mutingCoversTheSiteAndPagesItOpens() throws Exception {
        try (var server = new AuthenticationFixture(); var browser = new WindowsBrowserTest.Harness()) {
            BrowserAuthenticationTest.installPageScript(browser);
            InlineBrowserPagesTest.enable(browser);
            browser.load(server.site + "/start");

            assertFalse(isMuted(browser));

            setMuted(browser, true);
            assertTrue(isMuted(browser), "The site must be muted");

            browser.loads.clear();
            browser.eval("window.open('" + server.site + "/arbitrary-company-route','_blank')");
            assertEquals(Boolean.TRUE, browser.loads.poll(20, TimeUnit.SECONDS));
            boolean extraPage = call(browser, view -> view.windows().hasExtraPage());
            assertTrue(extraPage, "The site must open its page inside Spark");
            assertTrue(isMuted(browser), "A page opened by a muted site must start muted");

            setMuted(browser, false);
            assertFalse(isMuted(browser), "Unmuting must reach pages opened by the site");
        }
    }

    @Test
    void aSiteReportsWhenItPlaysSoundEvenWhileMuted() throws Exception {
        var reports = new LinkedBlockingQueue<Boolean>();

        try (var server = new AuthenticationFixture(); var browser = new WindowsBrowserTest.Harness()) {
            browser.load(server.site + "/start");
            call(browser, view -> {
                // Muted first, so the test stays silent; WebView2 still reports the sound.
                view.windows().setMuted(true);
                view.windows().observeAudio(reports::add);

                return null;
            });

            assertEquals(Boolean.FALSE, reports.poll(5, TimeUnit.SECONDS), "A quiet page reports no sound");

            var page = new NativeDevTools(browser);
            page.request("Runtime.evaluate", """
                    {"userGesture": true, "awaitPromise": true, "expression":
                     "(async () => { window.tone = new AudioContext(); const o = tone.createOscillator(); o.connect(tone.destination); o.start(); await tone.resume(); return tone.state; })()"}
                    """);

            assertEquals(Boolean.TRUE, reports.poll(10, TimeUnit.SECONDS), "Sound from a muted page is reported");

            page.evaluate("tone.close()");

            assertEquals(Boolean.FALSE, reports.poll(15, TimeUnit.SECONDS), "The end of the sound is reported");
        }
    }

    private static void setMuted(WindowsBrowserTest.Harness browser, boolean muted) throws Exception {
        call(browser, view -> {
            view.windows().setMuted(muted);

            return null;
        });
    }

    /** Reads the state of the page on screen, which is the opened page once the site opens one. */
    private static boolean isMuted(WindowsBrowserTest.Harness browser) throws Exception {
        return call(browser, view -> {
            var audio = ComCalls.query(view.windows().activeContext().getCore(), CORE_8);

            try {
                var muted = new IntByReference();
                ComCalls.check(ComCalls.call(audio, GET_IS_MUTED, muted));

                return muted.getValue() != 0;
            } finally {
                ComCalls.release(audio);
            }
        });
    }
}
