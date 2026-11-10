package to.sparkapp.app.browser;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.jna.Pointer;

/**
 * What a page calls itself: its title, and the name of its website when the page declares one.
 *
 * @param title the page's title, or an empty text
 * @param siteName the website's own name, such as {@code og:site_name}, or an empty text
 */
public record PageNames(String title, String siteName) {
    public static final PageNames NONE = new PageNames("", "");
    private static final String SCRIPT = """
            (() => {
              const meta = selector => document.querySelector(selector)?.content?.trim() || '';
              const siteName = meta('meta[property="og:site_name"]')
                || meta('meta[name="application-name"]')
                || meta('meta[name="apple-mobile-web-app-title"]');
              return { title: document.title.trim(), siteName };
            })()""";

    /** Reads the names of the page shown by a WebView2 core, on its owning thread. */
    static CompletableFuture<PageNames> read(Pointer core) {
        var parameters = Map.of("expression", SCRIPT, "returnByValue", true);

        return new BrowserDevTools(core).send("Runtime.evaluate", parameters).thenApply(PageNames::from);
    }

    static PageNames from(JsonNode evaluation) {
        var value = evaluation.path("result").path("value");

        return new PageNames(value.path("title").asText(""), value.path("siteName").asText(""));
    }
}
