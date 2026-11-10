package to.sparkapp.app.browser;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * What a site sees from a phone: the user agent of Chrome on Android with the browser's own version, and the
 * matching client hints, which some sites check instead of the user agent.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class MobileUserAgent {
    private static final Pattern CHROME_VERSION = Pattern.compile("Chrome/(\\d+)");
    /** Used when the browser's own user agent names no version. */
    private static final String FALLBACK_VERSION = "130";
    /** Chrome on Android reports this reduced device, as Chrome does since version 110. */
    private static final String AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) "
            + "Chrome/%s.0.0.0 Mobile Safari/537.36";

    static String agent(String desktopAgent) {
        return AGENT.formatted(majorVersion(desktopAgent));
    }

    /** The client hints in the form the DevTools protocol takes them. */
    static Map<String, Object> clientHints(String desktopAgent) {
        var version = majorVersion(desktopAgent);
        var brands = List.of(Map.of("brand", "Chromium", "version", version),
                Map.of("brand", "Not_A Brand", "version", "24"));

        return Map.of("brands", brands, "platform", "Android", "platformVersion", "10.0.0", "architecture", "",
                "model", "K", "mobile", true);
    }

    private static String majorVersion(String desktopAgent) {
        var match = desktopAgent == null ? null : CHROME_VERSION.matcher(desktopAgent);

        return match != null && match.find() ? match.group(1) : FALLBACK_VERSION;
    }
}
