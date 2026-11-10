package to.sparkapp.app.ui.topbar;

import java.util.function.Consumer;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import to.sparkapp.app.ui.topbar.components.ShellIcon;
import to.sparkapp.app.ui.topbar.components.ShellTooltip;
import to.sparkapp.app.ui.webview.SitePage;

/**
 * Zoom of one site: minus, the current zoom that resets it, and plus. The menu stays open while it is used.
 */
final class SiteZoomRow extends HBox {
    private static final double SMALLEST_PERCENT = 58;
    private static final double LARGEST_PERCENT = 207;
    private final Button zoomOut;
    private final Button reset;
    private final Button zoomIn;
    private SitePage page;

    SiteZoomRow() {
        zoomOut = step("−", "Zoom out\nOn the page: Ctrl + -", SitePage::zoomOut);
        reset = step("100%", "Reset zoom\nOn the page: Ctrl + 0", SitePage::resetZoom);
        reset.getStyleClass().setAll("button", "zoom-reset");
        reset.setMinWidth(52);
        zoomIn = step("+", "Zoom in\nOn the page: Ctrl + +", SitePage::zoomIn);

        var controls = new HBox(zoomOut, reset, zoomIn);
        controls.getStyleClass().add("zoom-controls");

        var space = new Region();
        HBox.setHgrow(space, Priority.ALWAYS);

        getChildren().addAll(ShellIcon.create("magnifying-glass"), new Label("Zoom"), space, controls);
        getStyleClass().add("zoom-row");
    }

    void show(SitePage page) {
        this.page = page;
        refresh();
    }

    private void refresh() {
        var percent = page.zoomPercent();

        reset.setText(Math.round(percent) + "%");
        reset.setDisable(!page.canZoom());
        zoomOut.setDisable(!page.canZoom() || percent <= SMALLEST_PERCENT);
        zoomIn.setDisable(!page.canZoom() || percent >= LARGEST_PERCENT);
    }

    private Button step(String text, String hint, Consumer<SitePage> command) {
        var button = new Button(text);
        button.getStyleClass().add("zoom-step");
        button.setMinSize(28, 28);
        button.setTooltip(ShellTooltip.create(hint));
        button.setAccessibleText(hint.split("\n")[0]);
        button.setOnAction(event -> {
            command.accept(page);
            refresh();
        });

        return button;
    }
}
