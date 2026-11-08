package to.sparkapp.app.browser;

import static org.junit.jupiter.api.Assertions.*;
import static to.sparkapp.app.browser.InlineBrowserPagesTest.call;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Tag("native")
@Timeout(40)
class BrowserTitleWatchTest {
    @Test
    void pageTitleChangesAreReported() throws Exception {
        var changes = new Semaphore(0);

        try (var browser = new WindowsBrowserTest.Harness()) {
            call(browser, view -> {
                view.windows().observeTitles(changes::release);

                return null;
            });
            browser.load("data:text/html,%3Ctitle%3EFirst%3C/title%3E");
            changes.drainPermits();

            browser.eval("document.title='Second'");

            assertTrue(changes.tryAcquire(10, TimeUnit.SECONDS), "A new title must be reported");
        }
    }
}
