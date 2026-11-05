package to.sparkapp.app.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UrlUtilsTest {
    @Test void rejectsInvalidAddressesAndNonWebSchemes() {
        for (String url : new String[]{"javascript:alert(1)", "file:///C:/test", "httpsbroken", "https://", "https://user:password@example.com"}) {
            assertFalse(UrlUtils.isWebUrl(url), url);
            assertFalse(UrlUtils.isSameHost(url, "https://example.com"), url);
        }
        assertFalse(UrlUtils.isWebUrl(null));
    }

    @Test void matchesHostsAtDomainBoundaries() {
        assertTrue(UrlUtils.isSameHost("https://chat.example.com/thread", "https://example.com"));
        assertTrue(UrlUtils.isSameHost("https://WWW.EXAMPLE.COM", "https://example.com"));
        assertFalse(UrlUtils.isSameHost("https://example.com.evil.test", "https://example.com"));
        assertFalse(UrlUtils.isSameHost("https://notexample.com", "https://example.com"));
        assertFalse(UrlUtils.isSameHost("https://example.com", null));
    }

    @Test void diagnosticsDoNotExposeCredentialsOrConversationPaths() {
        assertEquals("example.com", UrlUtils.forLog("https://example.com/chat/private?code=secret#token"));
        assertEquals("<invalid URL>", UrlUtils.forLog("https://user:secret@example.com"));
    }
}
