package to.sparkapp.app.browser;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;

class WebviewNavigatorTest {
    @Test
    void pageChangesUpdateRecoveryUrlWithoutRememberingAuthentication() {
        var navigator = new WebviewNavigator(null, null);
        var notifications = new ArrayList<String>();
        navigator.setOnUrlChanged(notifications::add);

        try {
            navigator.handleUrlChange("https://example.com/conversation/123");
            assertEquals("https://example.com/conversation/123", navigator.getCurrentUrl());
            navigator.handleUrlChange("https://accounts.google.com/login?code=secret");
            assertEquals("https://example.com/conversation/123", navigator.getCurrentUrl());
            assertEquals(2, notifications.size(), "Authentication must still notify the window focus handler");
            navigator.handleUrlChange(null);
            navigator.handleUrlChange("about:blank");
            assertEquals(2, notifications.size());
        } finally {
            navigator.shutdown();
        }
    }

    @Test
    void authenticationUsesParsedHostAndPath() {
        assertTrue(WebviewNavigator.isAuthUrl("https://accounts.google.com/o/oauth2/auth"));
        assertTrue(WebviewNavigator.isAuthUrl("https://example.com/login"));
        assertTrue(WebviewNavigator.isAuthUrl("https://example.com/oauth/callback"));
        assertFalse(WebviewNavigator.isAuthUrl("https://evil.test/?next=accounts.google.com/login"));
        assertFalse(WebviewNavigator.isAuthUrl("https://accounts.google.com.evil.test/"));
        assertFalse(WebviewNavigator.isAuthUrl("https://example.com/login-guide"));
        assertFalse(WebviewNavigator.isAuthUrl("file:///login"));
        assertFalse(WebviewNavigator.isAuthUrl(null));
        assertFalse(WebviewNavigator.isAuthUrl("https://github.com/project/repository"));
    }
}
