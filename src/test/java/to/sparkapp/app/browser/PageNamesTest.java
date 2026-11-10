package to.sparkapp.app.browser;

import static org.junit.jupiter.api.Assertions.*;
import static to.sparkapp.app.browser.InlineBrowserPagesTest.call;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Tag("native")
@Timeout(40)
class PageNamesTest {
    @Test
    void aPageReportsItsTitleAndTheNameOfItsSite() throws Exception {
        try (var browser = new WindowsBrowserTest.Harness()) {
            browser.load("data:text/html,%3Ctitle%3E An%20article%20-%20Example%20News %3C/title%3E"
                    + "%3Cmeta%20property=%22og:site_name%22%20content=%22Example%20News%22%3E");

            var names = read(browser);

            assertEquals("An article - Example News", names.title());
            assertEquals("Example News", names.siteName());
        }
    }

    @Test
    void aPageWithoutASiteNameReportsOnlyItsTitle() throws Exception {
        try (var browser = new WindowsBrowserTest.Harness()) {
            browser.load("data:text/html,%3Ctitle%3EPlain%3C/title%3E");

            assertEquals(new PageNames("Plain", ""), read(browser));
        }
    }

    private static PageNames read(WindowsBrowserTest.Harness browser) throws Exception {
        return call(browser, view -> PageNames.read(view.windows().activeContext().getCore())).get(10, TimeUnit.SECONDS);
    }
}
