package to.sparkapp.app.browser;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MobileUserAgentTest {
    private static final String DESKTOP = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/157.0.0.0 Safari/537.36 Edg/157.0.0.0";

    @Test
    void thePhoneRunsTheSameBrowserVersion() {
        var agent = MobileUserAgent.agent(DESKTOP);

        assertTrue(agent.contains("Android"));
        assertTrue(agent.contains("Chrome/157.0.0.0 Mobile Safari"));
        assertFalse(agent.contains("Windows"));
    }

    @Test
    void clientHintsDescribeAPhone() {
        var hints = MobileUserAgent.clientHints(DESKTOP);

        assertEquals(true, hints.get("mobile"));
        assertEquals("Android", hints.get("platform"));
        assertTrue(hints.get("brands").toString().contains("157"));
    }

    @Test
    void anUnknownBrowserStillGetsAPhoneAgent() {
        assertTrue(MobileUserAgent.agent("Something else").contains("Mobile Safari"));
        assertTrue(MobileUserAgent.agent(null).contains("Android"));
    }
}
