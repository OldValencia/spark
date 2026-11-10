package to.sparkapp.app.ui.webview;

/**
 * A page on screen that is not on its site: one the site opened, or one on another site it navigated to.
 *
 * @param siteName the site the page came from
 * @param address the page's address, or null until the page reports one
 */
public record OffSitePage(String siteName, String address) {
}
