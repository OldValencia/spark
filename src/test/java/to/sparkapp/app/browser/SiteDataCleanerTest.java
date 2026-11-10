package to.sparkapp.app.browser;

import static org.junit.jupiter.api.Assertions.*;
import static to.sparkapp.app.browser.InlineBrowserPagesTest.call;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Tag("native")
@Timeout(60)
class SiteDataCleanerTest {
    @Test
    void clearingOneSiteKeepsTheOtherSiteSignedIn() throws Exception {
        try (var server = new AuthenticationFixture(); var browser = new WindowsBrowserTest.Harness()) {
            var page = new NativeDevTools(browser);
            // The fixture sets a session cookie on each page; the two hosts stand for two sites.
            browser.load(server.identity + "/start");
            page.evaluate("localStorage.setItem('draft', 'other site')");
            browser.load(server.site + "/start");
            page.evaluate("localStorage.setItem('draft', 'this site')");

            assertTrue(cookies(page).contains("\"domain\":\"127.0.0.1\""));

            call(browser, view -> SiteDataCleaner.clear(view.windows().activeContext().getCore(), "127.0.0.1",
                    List.of(server.site))).get(10, TimeUnit.SECONDS);

            var remaining = cookies(page);

            assertFalse(remaining.contains("\"domain\":\"127.0.0.1\""), "The site's cookies are gone");
            assertTrue(remaining.contains("\"domain\":\"localhost\""), "The other site keeps its cookies");
            assertEquals("null", page.evaluate("String(localStorage.getItem('draft'))"), "The site's storage is gone");

            browser.load(server.identity + "/start");

            assertEquals("other site", page.evaluate("localStorage.getItem('draft')"), "The other site keeps storage");
        }
    }

    @Test
    void cookiesTheSiteReceivesAtItsAddressGoEvenFromAnotherDomain() throws Exception {
        try (var server = new AuthenticationFixture(); var browser = new WindowsBrowserTest.Harness()) {
            var page = new NativeDevTools(browser);
            browser.load(server.site + "/start");

            assertTrue(cookies(page).contains("\"domain\":\"127.0.0.1\""));

            // As with Gemini and .google.com: the session lives on a domain other than the site's own host.
            call(browser, view -> SiteDataCleaner.clear(view.windows().activeContext().getCore(), "gemini.example",
                    List.of(server.site))).get(10, TimeUnit.SECONDS);

            assertFalse(cookies(page).contains("\"domain\":\"127.0.0.1\""), "The session the site receives is gone");
        }
    }

    @Test
    void aSiteIncludesItsSubdomainsButNotLookalikes() {
        assertTrue(SiteDataCleaner.belongsTo("chatgpt.com", "chatgpt.com"));
        assertTrue(SiteDataCleaner.belongsTo("auth.chatgpt.com", "chatgpt.com"));
        assertFalse(SiteDataCleaner.belongsTo("notchatgpt.com", "chatgpt.com"));
        assertFalse(SiteDataCleaner.belongsTo("openai.com", "chatgpt.com"));
    }

    private static String cookies(NativeDevTools page) throws Exception {
        return page.request("Storage.getCookies", "{}").replace(" ", "");
    }
}
