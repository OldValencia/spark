package to.sparkapp.app.utils;

import java.awt.Desktop;
import java.net.URI;
import java.util.Collection;
import java.util.Locale;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UrlUtils {

    public static void openLink(String url) {
        if (!isWebUrl(url)) {
            return;
        }

        try {
            Desktop.getDesktop().browse(new URI(url));
        } catch (Exception e) {
            log.error("Error opening link", e);
        }
    }

    /**
     * Whether an address is a valid HTTP(S) URL without embedded credentials.
     */
    public static boolean isWebUrl(String url) {
        try {
            var uri = URI.create(url);

            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())) &&
                    uri.getHost() != null && uri.getUserInfo() == null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Removes user-controlled paths, queries and fragments from diagnostic output.
     */
    public static String forLog(String url) {
        var host = host(url);

        return host != null ? host : "<invalid URL>";
    }

    public static String host(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }

        try {
            var uri = URI.create(url);

            if (!isWebUrl(url)) {
                return null;
            }

            var host = uri.getHost();

            if (host == null) {
                return null;
            }

            host = host.toLowerCase(Locale.ROOT);

            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * True when both URLs point at the same host, treating {@code chat.example.com} as belonging to
     * {@code example.com}. Invalid URLs never match.
     */
    public static boolean isSameHost(String url, String baseUrl) {
        var host = host(url);
        var baseHost = host(baseUrl);

        if (host == null || baseHost == null) {
            return false;
        }

        return host.equals(baseHost) || host.endsWith("." + baseHost);
    }

    /**
     * The first URL of {@code baseUrls} that {@code url} belongs to, or {@code null}.
     */
    public static String matchingBaseUrl(String url, Collection<String> baseUrls) {
        if (host(url) == null || baseUrls == null) {
            return null;
        }

        for (var baseUrl : baseUrls) {
            if (host(baseUrl) != null && isSameHost(url, baseUrl)) {
                return baseUrl;
            }
        }

        return null;
    }
}
