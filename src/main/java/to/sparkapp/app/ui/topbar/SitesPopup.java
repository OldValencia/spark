package to.sparkapp.app.ui.topbar;

import java.util.Locale;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Screen;
import javafx.stage.Stage;
import to.sparkapp.app.utils.NativeWindowUtils;
import to.sparkapp.app.config.AiConfiguration;
import to.sparkapp.app.config.AiConfiguration.AiConfig;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.ui.topbar.components.AiDock;
import to.sparkapp.app.ui.topbar.components.DockItemNode;
import to.sparkapp.app.ui.topbar.components.ShellIcon;

/**
 * An owned native popup, so the list remains above the embedded browser.
 */
final class SitesPopup extends Popup {
    private final AiDock dock;
    private final Stage owner;
    private final TextField search = new TextField();
    private final ListView<AiConfig> list = new ListView<>();
    private final Label hint = new Label();
    private final VBox root = new VBox();
    private final SiteListDrag drag;
    private double maxListHeight = 324;

    SitesPopup(Stage owner, AiDock dock, AiConfiguration configuration, AppPreferences preferences, Runnable onChanged) {
        this.owner = owner;
        this.dock = dock;
        new PopupMotion(this, preferences::isReducedMotion);
        setAutoHide(true);
        setHideOnEscape(true);
        root.getStyleClass().add("shell-popup");
        root.getStylesheets().add(getClass().getResource("/css/shell.css").toExternalForm());
        root.setPrefWidth(360);

        var heading = new Label("YOUR SITES");
        heading.getStyleClass().add("shell-hint");
        search.setPromptText("Find a site…");
        search.setAccessibleText("Search sites");
        hint.getStyleClass().add("shell-hint");
        hint.setWrapText(true);
        list.setFixedCellSize(54);

        var editor = new SitesEditor(owner, configuration, onChanged);
        var context = new SiteListContext(dock, list, search, preferences, this::choose, site -> {
            hide();
            editor.edit(site);
        });
        drag = new SiteListDrag(context);
        list.setCellFactory(view -> new SiteListCell(context, drag));
        list.setPlaceholder(new Label("No matching sites"));
        list.setOnKeyPressed(e -> {
            var selected = list.getSelectionModel().getSelectedItem();

            if (e.getCode() == KeyCode.ENTER && selected != null) {
                choose(selected);
                e.consume();
            }
        });

        var add = new Button("Add any website", ShellIcon.create("plus"));
        add.getStyleClass().add("shell-row");
        add.setMaxWidth(Double.MAX_VALUE);
        add.setOnAction(e -> {
            hide();
            editor.edit(null);
        });
        root.getChildren().addAll(heading, search, hint, list, add);
        getContent().add(root);
        search.textProperty().addListener((o, before, text) -> refresh());
        setOnShowing(e -> {
            search.clear();
            refresh();
        });
        setOnShown(e -> {
            // The site browser may hold Windows keyboard focus after switching tabs.
            NativeWindowUtils.focusWindow(getOwnerWindow());
            search.requestFocus();
        });
        setOnHidden(e -> {
            drag.cancel();
        });
        drag.install();
        dock.addOrderListener(this::refresh);
    }

    void open(Node anchor) {
        if (isShowing()) {
            hide();

            return;
        }

        root.setPrefWidth(Math.clamp(owner.getWidth() - 20, 280, 360));

        var bounds = anchor.localToScreen(anchor.getBoundsInLocal());

        if (bounds != null) {
            var screen = Screen.getScreensForRectangle(bounds.getMinX(), bounds.getMinY(), 1, 1).stream().findFirst()
                    .orElse(Screen.getPrimary());
            maxListHeight = Math.clamp(screen.getVisualBounds().getHeight() - 180, 54, 324);
            show(owner, Math.min(bounds.getMinX(), owner.getX() + owner.getWidth() - root.getPrefWidth() - 8),
                    bounds.getMaxY() + 8);
        }
    }

    private void refresh() {
        var bar = list.lookupAll(".scroll-bar").stream()
                .filter(n -> n instanceof ScrollBar b && b.getOrientation() == Orientation.VERTICAL)
                .map(n -> (ScrollBar) n).findFirst().orElse(null);
        double scroll = bar == null ? 0 : bar.getValue();
        boolean restore = drag.isPreserveScroll();
        var query = search.getText().trim().toLowerCase(Locale.ROOT);
        var filtered = dock.getDockItems().stream().map(DockItemNode::getConfig)
                .filter(site -> (site.name() + " " + site.url()).toLowerCase(Locale.ROOT).contains(query)).toList();
        list.setItems(FXCollections.observableArrayList(filtered));
        list.setPrefHeight(Math.clamp(filtered.size() * 54, 60, maxListHeight));
        hint.setText(
                query.isEmpty() ? "Drag a grip to reorder · Alt + ↑ / ↓ on a grip" : "Clear search to reorder all sites");

        if (restore && bar != null) {
            Platform.runLater(() -> bar.setValue(scroll));
        }
    }

    private void choose(AiConfig site) {
        dock.selectSite(site.id());
        hide();
    }
}
