package to.sparkapp.app.ui.topbar;

import java.util.List;
import java.util.Locale;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.Button;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import to.sparkapp.app.config.AiConfiguration.AiConfig;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.config.MuteRule;
import to.sparkapp.app.ui.topbar.components.AiDock;
import to.sparkapp.app.ui.topbar.components.DockItemNode;
import to.sparkapp.app.ui.webview.FxWebViewPane;
import to.sparkapp.app.ui.webview.SitePage;
import to.sparkapp.app.utils.UrlUtils;

/**
 * Menu of a site tab, opened by a right-click. Its actions apply to that site, even when another one is open.
 */
final class SiteTabMenu extends ShellPopover {
    private final AiDock dock;
    private final FxWebViewPane browser;
    private final AppPreferences preferences;
    private final BooleanProperty muted = new SimpleBooleanProperty();
    private final BooleanProperty mutedWhileHidden = new SimpleBooleanProperty();
    private final BooleanProperty mobile = new SimpleBooleanProperty();
    private final SiteZoomRow zoom = new SiteZoomRow();
    private final List<Button> pageActions;
    private final Button moveLeft;
    private final Button moveRight;
    private DockItemNode tab;
    private SitePage page;

    SiteTabMenu(AiDock dock, FxWebViewPane browser, SitesEditor editor, AppPreferences preferences) {
        super("SITE", 300, preferences::isReducedMotion);
        this.dock = dock;
        this.browser = browser;
        this.preferences = preferences;

        var back = action("arrow-uturn-left", "Back", () -> page.goBack());
        var reload = action("arrow-path", "Reload", () -> page.reload());
        pageActions = List.of(back, reload);
        add(zoom);
        setting("device-phone-mobile", "Mobile version", mobile, this::toggleMobile);
        separator();

        action("link", "Copy link", this::copyLink);
        action("arrow-top-right-on-square", "Open in browser", () -> UrlUtils.openLink(page.address()));
        separator();

        setting("speaker-x-mark", "Mute site", muted, () -> toggleMute(MuteRule.ALWAYS, muted));
        setting("eye-slash", "Mute while Spark is hidden", mutedWhileHidden,
                () -> toggleMute(MuteRule.WHILE_HIDDEN, mutedWhileHidden));
        separator();

        moveLeft = action("arrow-left", "Move left", () -> dock.move(site().id(), -1));
        moveRight = action("arrow-right", "Move right", () -> dock.move(site().id(), 1));
        separator();

        action("pencil-square", "Edit site…", () -> editor.edit(site()));
    }

    /** Opens the menu under a tab, or closes it when it is already open for that tab. */
    void open(DockItemNode target) {
        if (isShowing()) {
            var sameTab = tab == target;
            dismiss();

            if (sameTab) {
                return;
            }
        }

        tab = target;
        refresh();
        toggle(target);
    }

    private void refresh() {
        page = browser.page(site());
        setHeading(site().name().toUpperCase(Locale.ROOT));

        pageActions.forEach(action -> action.setDisable(!page.isOpen()));
        zoom.show(page);

        mobile.set(preferences.isSiteMobile(site().id()));
        muted.set(preferences.isSiteMuted(site().id(), MuteRule.ALWAYS));
        mutedWhileHidden.set(preferences.isSiteMuted(site().id(), MuteRule.WHILE_HIDDEN));

        var index = dock.indexOf(site().id());
        moveLeft.setDisable(index <= 0);
        moveRight.setDisable(index >= dock.getDockItems().size() - 1);
    }

    /** The site's page is loaded again in the other version right away. */
    private void toggleMobile() {
        mobile.set(!mobile.get());
        preferences.setSiteMobile(site().id(), mobile.get());
        browser.setMobile(site().id(), mobile.get());
    }

    private void toggleMute(MuteRule rule, BooleanProperty state) {
        state.set(!state.get());
        preferences.setSiteMuted(site().id(), rule, state.get());
        browser.refreshSound();
        dock.refreshSound();
    }

    private AiConfig site() {
        return tab.getConfig();
    }

    private void copyLink() {
        var content = new ClipboardContent();
        content.putString(page.address());
        Clipboard.getSystemClipboard().setContent(content);
    }
}
