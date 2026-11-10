package to.sparkapp.app.ui.topbar;

import java.util.function.Consumer;
import java.util.regex.Pattern;

import javafx.application.Platform;
import lombok.RequiredArgsConstructor;
import to.sparkapp.app.browser.PageNames;
import to.sparkapp.app.ui.webview.FxWebViewPane;
import to.sparkapp.app.ui.webview.OffSitePage;
import to.sparkapp.app.utils.UrlUtils;

/**
 * Turns a page that is not on its site into a site of its own: the new site opens on that page, and the site it came
 * from goes back to where it was.
 */
@RequiredArgsConstructor
public final class PageSiteAdder {
    /** Longer names usually describe the page rather than the site, and do not fit a tab. */
    private static final int LONGEST_NAME = 30;
    /** Titles such as "Article | Site" or "Article - Site" end with the site's name. */
    private static final Pattern TITLE_SEPARATOR = Pattern.compile(" [|\\-–—·:]{1,2} ");
    private final FxWebViewPane browser;
    private final SitesEditor editor;
    /** Opens a site once the tabs include it. */
    private final Consumer<String> openSite;

    public void add(OffSitePage page) {
        var address = page.address();

        if (!UrlUtils.isWebUrl(address)) {
            return;
        }

        browser.pageNames().thenAccept(names -> Platform.runLater(() -> edit(address, names)));
    }

    private void edit(String address, PageNames names) {
        editor.add(suggestedName(names, address), address, id -> {
            browser.returnToSite();
            openSite.accept(id);
        });
    }

    /**
     * The name the site gives itself, else the last part of a title such as "Article | Site", else a short title,
     * else the page's host.
     */
    static String suggestedName(PageNames names, String address) {
        var title = names.title().strip();
        var parts = TITLE_SEPARATOR.split(title);
        var lastPart = parts.length > 1 ? parts[parts.length - 1] : "";

        for (var candidate : new String[]{names.siteName().strip(), lastPart.strip(), title}) {
            if (fitsTab(candidate)) {
                return candidate;
            }
        }

        var host = UrlUtils.host(address);

        return host != null ? host : "";
    }

    private static boolean fitsTab(String name) {
        return !name.isEmpty() && name.length() <= LONGEST_NAME && !UrlUtils.isWebUrl(name);
    }
}
