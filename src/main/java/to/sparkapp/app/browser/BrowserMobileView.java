package to.sparkapp.app.browser;

import static to.sparkapp.app.browser.ComCalls.*;

import java.util.HashMap;

import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.Guid.GUID;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.ptr.PointerByReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Shows one browsing context the mobile version of sites. Runs on the owning WebView2 thread.
 *
 * <p>The user agent is set in the WebView2 settings, so it applies from the next request on; the client hints are set
 * through the DevTools protocol, which also covers the user agent seen by scripts.
 */
@Slf4j
@RequiredArgsConstructor
final class BrowserMobileView {
    private static final int CORE_GET_SETTINGS = 3;
    private static final String SETTINGS_2 = "ee9a0f68-f46c-4e32-ac23-ef8cac224d2a";
    private static final int GET_USER_AGENT = 21;
    private static final int PUT_USER_AGENT = 22;

    private final Pointer core;
    private String desktopAgent;
    /** A new page shows the desktop version, so a site that is never mobile needs no call at all. */
    private boolean mobile;

    void setMobile(boolean value) {
        if (mobile == value) {
            return;
        }

        var settings = settings();

        if (settings == null) {
            return;
        }

        try {
            if (desktopAgent == null) {
                desktopAgent = readAgent(settings);
            }

            var agent = value ? MobileUserAgent.agent(desktopAgent) : desktopAgent;
            check(call(settings, PUT_USER_AGENT, new WString(agent)));
            mobile = value;
            overrideClientHints(value, agent);
            log.info("Page shows the {} version", value ? "mobile" : "desktop");
        } catch (RuntimeException failure) {
            log.warn("Could not change the page's version: {}", failure.getMessage());
        } finally {
            release(settings);
        }
    }

    /** Clearing the override with an empty user agent returns the browser's own client hints. */
    private void overrideClientHints(boolean value, String agent) {
        var parameters = new HashMap<String, Object>();
        parameters.put("userAgent", value ? agent : "");

        if (value) {
            parameters.put("userAgentMetadata", MobileUserAgent.clientHints(desktopAgent));
        }

        new BrowserDevTools(core).send("Emulation.setUserAgentOverride", parameters).whenComplete((result, error) -> {
            if (error != null) {
                log.warn("Could not change the page's client hints: {}", error.getMessage());
            }
        });
    }

    private Pointer settings() {
        var settings = new PointerByReference();

        if (call(core, CORE_GET_SETTINGS, settings) < 0) {
            return null;
        }

        var settings2 = new PointerByReference();
        int result = call(settings.getValue(), 0, new GUID(SETTINGS_2), settings2);
        release(settings.getValue());

        if (result < 0) {
            log.debug("This WebView2 Runtime cannot change the user agent: 0x{}", Integer.toHexString(result));

            return null;
        }

        return settings2.getValue();
    }

    private static String readAgent(Pointer settings) {
        var out = new PointerByReference();
        check(call(settings, GET_USER_AGENT, out));

        try {
            return out.getValue().getWideString(0);
        } finally {
            Ole32.INSTANCE.CoTaskMemFree(out.getValue());
        }
    }
}
