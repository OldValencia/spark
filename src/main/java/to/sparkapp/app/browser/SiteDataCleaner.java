package to.sparkapp.app.browser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.jna.Pointer;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Clears the cookies and stored data of one site, leaving other sites signed in. Runs on the owning WebView2 thread.
 * All sites share one browser profile, so any page of Spark can clear any site.
 *
 * <p>The cookies cleared are those the site receives at its addresses, including ones set for a parent domain, such
 * as {@code .google.com} for Gemini, and those of its subdomains, such as a sign-in subdomain.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SiteDataCleaner {
    /**
     * @param host    the site's host without {@code www.}; its subdomains are cleared too
     * @param origins the site's origins; their cookies and storage are cleared
     */
    static CompletableFuture<Void> clear(Pointer core, String host, Collection<String> origins) {
        var tools = new BrowserDevTools(core);
        var urls = origins.stream().map(origin -> origin + "/").toList();
        var received = tools.send("Network.getCookies", Map.of("urls", urls));
        var stored = tools.send("Storage.getCookies", Map.of());

        return received.thenCombine(stored, (forSite, all) -> cookiesOf(forSite, all, host))
                .thenCompose(cookies -> remove(tools, cookies, host, origins));
    }

    private static Collection<JsonNode> cookiesOf(JsonNode forSite, JsonNode all, String host) {
        var cookies = new LinkedHashMap<String, JsonNode>();
        forSite.path("cookies").forEach(cookie -> cookies.put(key(cookie), cookie));

        for (var cookie : all.path("cookies")) {
            if (belongsTo(bare(cookie), host)) {
                cookies.put(key(cookie), cookie);
            }
        }

        return cookies.values();
    }

    private static CompletableFuture<Void> remove(BrowserDevTools tools, Collection<JsonNode> cookies, String host,
                                                  Collection<String> origins) {
        var steps = new ArrayList<CompletableFuture<JsonNode>>();
        var storage = new LinkedHashSet<>(origins);

        for (var cookie : cookies) {
            steps.add(tools.send("Network.deleteCookies", Map.of("name", cookie.path("name").asText(),
                    "domain", cookie.path("domain").asText(), "path", cookie.path("path").asText("/"))));

            // Storage of a parent domain belongs to the shared account, not to this site.
            if (belongsTo(bare(cookie), host)) {
                storage.add("https://" + bare(cookie));
            }
        }

        for (var origin : storage) {
            steps.add(tools.send("Storage.clearDataForOrigin", Map.of("origin", origin, "storageTypes", "all")));
        }

        return CompletableFuture.allOf(steps.toArray(CompletableFuture[]::new));
    }

    static boolean belongsTo(String domain, String host) {
        return domain.equals(host) || domain.endsWith("." + host);
    }

    private static String bare(JsonNode cookie) {
        var domain = cookie.path("domain").asText("");

        return domain.startsWith(".") ? domain.substring(1) : domain;
    }

    private static String key(JsonNode cookie) {
        return String.join("|", List.of(cookie.path("name").asText(), cookie.path("domain").asText(),
                cookie.path("path").asText()));
    }
}
