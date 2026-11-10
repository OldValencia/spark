package to.sparkapp.app.readme;

import java.awt.event.KeyEvent;
import java.util.function.Predicate;

import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.stage.Popup;
import javafx.stage.Window;
import to.sparkapp.app.config.AiConfiguration.AiConfig;
import to.sparkapp.app.ui.topbar.components.AiDock;
import to.sparkapp.app.ui.webview.FxWebViewPane;
import to.sparkapp.app.windows.MainWindow;

/** The steps the README capture takes in Spark's shell, shared by the tour and the screenshots. */
final class ReadmeShell {
    /** A page on another website, which Spark shows with the bar above it. */
    static final String OFF_SITE_ADDRESS = "https://www.wikimedia.org/";

    private static final String REJECT_COOKIES = "[...document.querySelectorAll('button')]"
            + ".find(b => /reject non-essential/i.test(b.textContent))?.click()";

    private final ReadmeMouse mouse;
    private final MainWindow spark;
    private final FxWebViewPane browser;

    ReadmeShell(ReadmeMouse mouse, MainWindow spark, FxWebViewPane browser) {
        this.mouse = mouse;
        this.spark = spark;
        this.browser = browser;
    }

    /** Matches a control whose accessible text starts with the prefix, such as a menu setting and its state. */
    static Predicate<Node> startingWith(String prefix) {
        return node -> node.getAccessibleText() != null && node.getAccessibleText().startsWith(prefix);
    }

    /** Loads both capture sites fresh and declines ChatGPT's optional cookies. */
    void prepareSites() throws Exception {
        reloadSite("Wikipedia");
        reloadSite("ChatGPT");
        browser.extensionBrowser().eval(REJECT_COOKIES);
        ReadmeFx.pause(1_500);
    }

    /**
     * Clicks a site's tab until that site is on screen. A click can be spent closing a menu that is
     * still open, so it is retried a few times.
     */
    void openSite(String name) throws Exception {
        for (var attempt = 0; attempt < 3; attempt++) {
            mouse.click(ReadmeUi.control(name));

            if (awaitSiteOnScreen(name)) {
                return;
            }
        }

        throw new IllegalStateException(name + " did not come on screen");
    }

    void place(double x, double y, double width, double height) {
        spark.setAlwaysOnTop(true);
        spark.setX(x);
        spark.setY(y);
        spark.setWidth(width);
        spark.setHeight(height);
        spark.toFront();
    }

    /**
     * Clicks a header button until the expected menu item appears. A click can be spent closing a
     * popup that is still open, so it is retried a few times.
     */
    Point2D openMenu(String button, Predicate<Node> item) throws Exception {
        for (var attempt = 0; attempt < 3; attempt++) {
            mouse.click(ReadmeUi.control(button));
            var found = ReadmeUi.tryCenter(item, 1_500);

            if (found != null) {
                ReadmeFx.pause(400);
                return found;
            }
        }

        return ReadmeUi.center(item);
    }

    void openFromOptions(String action) throws Exception {
        mouse.click(openMenu("Window options", ReadmeUi.named(action)));
        ReadmeFx.pause(900);
    }

    void choosePreset(String prefix) throws Exception {
        mouse.click(openMenu("Window size", startingWith(prefix)));
        ReadmeFx.pause(400);
    }

    /**
     * Presses Escape to close Spark's open menu. A menu that keeps showing, because the key went
     * elsewhere, is closed directly so it cannot cover the next screenshot.
     */
    void closeMenus() throws Exception {
        mouse.key(KeyEvent.VK_ESCAPE);
        ReadmeFx.pause(500);
        ReadmeFx.run(() -> Window.getWindows().stream()
                .filter(window -> window instanceof Popup && window.isShowing())
                .toList()
                .forEach(Window::hide));
    }

    /** Right-clicks a site's tab and waits for its menu. */
    void openTabMenu(String site) throws Exception {
        mouse.rightClick(ReadmeUi.control(site));
        ReadmeUi.center(startingWith("Mobile version"));
        ReadmeFx.pause(400);
    }

    /** Ctrl + Shift + L, which opens All sites with its search ready. */
    void findSite() throws Exception {
        mouse.key(KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_L);
        ReadmeUi.center(ReadmeUi.named("Search sites"));
        ReadmeFx.pause(400);
    }

    /** Follows the open site to a page on another website, as a link would, and waits for the bar above it. */
    void leaveSite(String site) throws Exception {
        browser.extensionBrowser().eval("location.assign('" + OFF_SITE_ADDRESS + "')");
        ReadmeUi.center(ReadmeUi.named("Back to " + site));
        ReadmeFx.pause(2_500);
    }

    void returnToSite(String site) throws Exception {
        mouse.click(ReadmeUi.control("Back to " + site));
        ReadmeFx.pause(1_500);
    }

    private void reloadSite(String name) throws Exception {
        openSite(name);
        ReadmeFx.pause(1_500);
        browser.extensionBrowser().reload();
        ReadmeFx.pause(5_000);
    }

    private static boolean awaitSiteOnScreen(String name) throws Exception {
        var deadline = System.nanoTime() + 1_500_000_000L;

        while (System.nanoTime() < deadline) {
            if (name.equals(ReadmeFx.call(ReadmeShell::siteOnScreen))) {
                return true;
            }

            ReadmeFx.pause(100);
        }

        return false;
    }

    private static String siteOnScreen() {
        return ReadmeUi.find(AiDock.class::isInstance)
                .map(node -> ((AiDock) node).getSelectedSite())
                .map(AiConfig::name)
                .orElse(null);
    }
}
