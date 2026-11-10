package to.sparkapp.app.browser;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;
import to.sparkapp.app.config.AiConfiguration.AiConfig;

class WebviewNavigatorTest {
    @Test
    void arbitraryIdentityProviderPagesStayInTheBrowserWithoutReplacingTheRecoveryAddress() {
        var notifications = new ArrayList<String>();
        var queued = new CopyOnWriteArrayList<Runnable>();
        var navigator = new WebviewNavigator(queued::add, url -> {
        }, () -> {
        });
        navigator.setOnUrlChanged(notifications::add);

        try {
            navigator.setCurrentConfig(new AiConfig("site", "Site", "https://example.com", null, null), null);
            navigator.handleUrlChange("https://example.com/conversation/123");
            navigator.handleUrlChange("https://identity.test/logowanie");
            assertTrue(navigator.isExternalPage("https://identity.test/logowanie"));
            assertEquals("https://example.com/conversation/123", navigator.getCurrentUrl());
            navigator.handleUrlChange("https://identity.test/any/path/chosen/by/the/company");
            navigator.handleUrlChange("https://example.com/any-return-path?unknown_parameter=private-value");
            assertEquals("https://example.com/conversation/123", navigator.getCurrentUrl());
            assertEquals(4, notifications.size());
            navigator.handleUrlChange("https://example.com/logowanie");
            assertFalse(navigator.isExternalPage("https://example.com/logowanie"));
            assertEquals("https://example.com/logowanie", navigator.getCurrentUrl(), "No path names are classified as login");
            navigator.handleUrlChange(null);
            navigator.handleUrlChange("about:blank");
            assertEquals(5, notifications.size());
            var explicitStart = "https://example.com/search?configured=value#view";
            navigator.setCurrentConfig(new AiConfig("search", "Search", explicitStart, null, null), null);
            assertEquals(explicitStart, navigator.getCurrentUrl(), "An explicit start URL remains usable for crash recovery");
        } finally {
            navigator.shutdown();
        }
    }

    @Test
    void returningFromAnotherSiteOpensTheLastPageOnTheSite() {
        var loaded = new ArrayList<String>();
        var navigator = new WebviewNavigator(Runnable::run, loaded::add, () -> {
        });

        try {
            navigator.setCurrentConfig(new AiConfig("site", "Site", "https://example.com", null, null), null);
            navigator.handleUrlChange("https://example.com/conversation/123");
            navigator.handleUrlChange("https://docs.other.test/guide?section=2");

            assertTrue(navigator.isExternalPage());
            assertEquals("https://docs.other.test/guide?section=2", navigator.getPageUrl());

            navigator.returnToSitePage();

            assertEquals("https://example.com/conversation/123", loaded.getLast());

            navigator.forgetPageUrl();

            assertNull(navigator.getPageUrl(), "A page the site opened reports its own address later");
        } finally {
            navigator.shutdown();
        }
    }
}
