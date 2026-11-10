package to.sparkapp.app.ui.webview;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import javafx.application.Platform;
import javafx.stage.Window;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import to.sparkapp.app.browser.WebviewManager;
import to.sparkapp.app.config.AiConfiguration.AiConfig;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.ui.dialogs.ShellMessageDialog;
import to.sparkapp.app.utils.UrlUtils;

@RequiredArgsConstructor
final class SiteDataClearing {
    private final BrowserSessions sessions;
    private final AppPreferences preferences;
    private final Supplier<Window> owner;
    private final BooleanSupplier disposed;
    @Getter
    private boolean clearing;

    void clear(WebviewManager browser) {
        new ShellMessageDialog(owner.get(), "Sign out of all sites?", "Cancel")
                .text("Clear cookies and website storage for all sites in Spark. This signs you out and reloads open "
                        + "sites.")
                .hint("Unsent text will be lost.")
                .confirm("Sign out", () -> {
                    clearing = true;
                    sessions.forEach(WebviewManager::stopLoading);
                    browser.clearBrowsingData(this::complete);
                })
                .show();
    }

    private void complete(boolean success) {
        clearing = false;

        if (disposed.getAsBoolean()) {
            return;
        }

        if (success) {
            preferences.clearSiteHistory();
            sessions.forEach(WebviewManager::reload);
            new ShellMessageDialog(owner.get(), "Site data cleared", "Done")
                    .text("Cookies and stored website data have been cleared for the Spark browser profile.")
                    .show();
        } else {
            showFailure();
        }
    }

    /** Signs out of one site after asking: its cookies and stored data go, and its page reloads. */
    void clearSite(AiConfig site, WebviewManager anyBrowser) {
        var host = UrlUtils.host(site.url());

        if (host == null) {
            return;
        }

        new ShellMessageDialog(owner.get(), "Sign out of " + site.name() + "?", "Cancel")
                .text("Clear the cookies and website storage of " + site.name() + " and reload it. Other sites stay "
                        + "signed in, except those that share its account, such as other Google sites.")
                .hint("If you signed in with an account of another site, such as Google, that account stays signed "
                        + "in, and the site may let you back in with one click. Unsent text on this site will be lost.")
                .confirm("Sign out", () -> {
                    clearing = true;
                    anyBrowser.clearSiteData(host, origins(site, host)).whenComplete(
                            (done, failure) -> Platform.runLater(() -> siteCleared(site, failure == null)));
                })
                .show();
    }

    /** The site's own address and the page it shows now, with and without {@code www.}. */
    private Set<String> origins(AiConfig site, String host) {
        var origins = new LinkedHashSet<String>();
        origins.add(origin(site.url()));
        origins.add("https://" + host);
        origins.add("https://www." + host);

        var browser = sessions.find(site.id());
        var page = browser == null ? null : browser.getCurrentUrl();

        if (UrlUtils.isSameHost(page, site.url())) {
            origins.add(origin(page));
        }

        origins.remove(null);

        return origins;
    }

    private static String origin(String url) {
        try {
            var uri = URI.create(url);
            var port = uri.getPort() < 0 ? "" : ":" + uri.getPort();

            return uri.getScheme() + "://" + uri.getHost() + port;
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private void siteCleared(AiConfig site, boolean success) {
        clearing = false;

        if (disposed.getAsBoolean()) {
            return;
        }

        if (!success) {
            showFailure();

            return;
        }

        var browser = sessions.find(site.id());

        if (browser != null) {
            browser.reload();
        }
    }

    private void showFailure() {
        new ShellMessageDialog(owner.get(), "Site data could not be cleared", "OK")
                .text("Please try again or check the application log.")
                .show();
    }
}
