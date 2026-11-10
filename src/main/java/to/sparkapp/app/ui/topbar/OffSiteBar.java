package to.sparkapp.app.ui.topbar;

import java.util.function.Consumer;

import javafx.beans.value.ObservableValue;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import to.sparkapp.app.ui.topbar.components.ShellIcon;
import to.sparkapp.app.ui.topbar.components.ShellTooltip;
import to.sparkapp.app.ui.webview.OffSitePage;
import to.sparkapp.app.utils.UrlUtils;

/**
 * Shown above a page that is not on its site, as in an app's built-in browser: where the page is, a way back to the
 * site, a way to keep the page as a site of its own, and a way to continue in the default browser.
 */
public final class OffSiteBar extends HBox {
    private static final double HEIGHT = 34;
    private final Button back = TopBarArea.button("x-mark", "Back to the site");
    private final Label address = new Label();
    private final Button addAsSite = TopBarArea.button("plus", "Add as a site");
    private final Button openInBrowser = new Button("Open in browser", ShellIcon.create("arrow-top-right-on-square"));
    private OffSitePage page;

    /**
     * @param offSitePage the page on screen when it is not on its site, or null
     * @param returnToSite leaves that page and shows the site again
     * @param addSite adds that page as a site of its own
     */
    public OffSiteBar(ObservableValue<OffSitePage> offSitePage, Runnable returnToSite, Consumer<OffSitePage> addSite) {
        back.setOnAction(event -> returnToSite.run());
        addAsSite.setOnAction(event -> addSite.accept(page));

        address.getStyleClass().add("off-site-address");
        address.setMinWidth(0);
        HBox.setHgrow(address, Priority.ALWAYS);
        address.setMaxWidth(Double.MAX_VALUE);

        openInBrowser.getStyleClass().addAll("shell-button", "off-site-open");
        openInBrowser.setGraphicTextGap(6);
        openInBrowser.setMinWidth(Region.USE_PREF_SIZE);
        openInBrowser.setOnAction(event -> UrlUtils.openLink(page.address()));

        getChildren().addAll(back, address, addAsSite, openInBrowser);
        getStyleClass().add("off-site-bar");
        setMinHeight(HEIGHT);
        setPrefHeight(HEIGHT);
        setMaxHeight(HEIGHT);
        managedProperty().bind(visibleProperty());

        offSitePage.addListener((observable, before, shown) -> show(shown));
        show(offSitePage.getValue());
    }

    private void show(OffSitePage shown) {
        page = shown;
        setVisible(shown != null);

        if (shown == null) {
            return;
        }

        var host = UrlUtils.host(shown.address());
        var hint = "Back to " + shown.siteName();

        address.setText(host != null ? host : "Page opened by " + shown.siteName());
        back.setAccessibleText(hint);
        back.setTooltip(ShellTooltip.create(hint));

        var hasAddress = UrlUtils.isWebUrl(shown.address());
        addAsSite.setDisable(!hasAddress);
        openInBrowser.setDisable(!hasAddress);
    }
}
