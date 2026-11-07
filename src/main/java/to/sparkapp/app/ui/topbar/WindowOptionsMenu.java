package to.sparkapp.app.ui.topbar;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.ui.topbar.components.ShellIcon;
import to.sparkapp.app.ui.topbar.components.ShellTooltip;
import to.sparkapp.app.ui.webview.FxWebViewPane;

final class WindowOptionsMenu extends ShellPopover {
    WindowOptionsMenu(AppPreferences preferences, FxWebViewPane browser, Runnable onSettings) {
        super("WINDOW OPTIONS", 258, preferences::isReducedMotion);
        action("cog-6-tooth", "Settings", onSettings);

        var zoomOut = zoomButton("−", "Zoom out\nOn the page: Ctrl + -", browser::zoomOut);
        var reset = zoomButton("100%", "Reset zoom\nOn the page: Ctrl + 0", browser::resetZoom);
        reset.getStyleClass().setAll("button", "zoom-reset");
        reset.setMinWidth(52);

        var zoomIn = zoomButton("+", "Zoom in\nOn the page: Ctrl + +", browser::zoomIn);
        var zoomControls = new HBox(zoomOut, reset, zoomIn);
        zoomControls.getStyleClass().add("zoom-controls");

        var space = new Region();
        HBox.setHgrow(space, Priority.ALWAYS);

        var zoomRow = new HBox(ShellIcon.create("magnifying-glass"), new Label("Zoom"), space, zoomControls);
        zoomRow.getStyleClass().add("zoom-row");
        add(zoomRow);
        browser.setZoomCallback(percent -> {
            reset.setText(Math.round(percent) + "%");
            zoomOut.setDisable(!preferences.isZoomEnabled() || percent <= 58);
            zoomIn.setDisable(!preferences.isZoomEnabled() || percent >= 207);
        });
    }

    private static Button zoomButton(String text, String hint, Runnable command) {
        var button = new Button(text);
        button.getStyleClass().add("zoom-step");
        button.setMinSize(28, 28);
        button.setTooltip(ShellTooltip.create(hint));
        button.setAccessibleText(hint.split("\n")[0]);
        button.setOnAction(e -> command.run());

        return button;
    }
}
