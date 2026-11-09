package to.sparkapp.app.ui.topbar;

import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import lombok.Getter;
import to.sparkapp.app.config.AiConfiguration;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.ui.topbar.components.AiDock;
import to.sparkapp.app.ui.topbar.components.ShellIcon;
import to.sparkapp.app.ui.topbar.components.ShellTooltip;
import to.sparkapp.app.ui.topbar.components.SparkMascot;
import to.sparkapp.app.ui.topbar.components.MascotReaction;
import to.sparkapp.app.ui.webview.FxWebViewPane;
import to.sparkapp.app.windows.WindowController;

public class TopBarArea extends HBox {
    public static final int HEIGHT = 52;
    /** Room the bar keeps for the mascot, buttons and drag handle next to the tabs. */
    private static final double RESERVED_WIDTH = 250;
    /** The mascot's width and its gap to the tabs. */
    private static final double MASCOT_WIDTH = 46;
    @Getter
    private final AiDock dock;
    private final SitesPopup sites;
    private final ShellPopover sizes;
    private final ShellPopover options;
    @Getter
    private final SparkMascot mascot;
    @Getter
    private final Button sitesButton;
    @Getter
    private final Button sizeButton;
    @Getter
    private final Button optionsButton;

    public TopBarArea(AiConfiguration configuration, FxWebViewPane browser, Stage frame, AppPreferences preferences, Runnable onSettings, Runnable onHide, WindowController controller, Runnable onSitesChanged) {
        this(configuration, browser, frame, preferences, onSettings, onHide, controller, onSitesChanged, () -> {});
    }

    public TopBarArea(AiConfiguration configuration, FxWebViewPane browser, Stage frame, AppPreferences preferences, Runnable onSettings, Runnable onHide, WindowController controller, Runnable onSitesChanged, Runnable onExtensions) {
        mascot = new SparkMascot(frame, preferences);
        mascot.observePage(browser.pageLoadingProperty(), browser.pageFailedProperty());
        sizes = new WindowSizeMenu(frame, preferences, controller);
        options = new WindowOptionsMenu(preferences, browser, () -> {
            mascot.react(MascotReaction.SETTINGS);
            onSettings.run();
        }, onExtensions);
        getStyleClass().add("shell-bar");
        setMinHeight(HEIGHT);
        setPrefHeight(HEIGHT);
        setMaxHeight(HEIGHT);

        dock = new AiDock(configuration.getConfigurations(), browser, preferences, browser.getActiveSiteId());
        dock.setOnSiteSelected(() -> mascot.react(MascotReaction.SITE_SWITCH));

        double dockBudget = dock.desiredWidth();
        dock.prefWidthProperty().bind(Bindings.createDoubleBinding(
                () -> Math.max(36, Math.min(dockBudget, frame.getWidth() - reservedWidth())),
                frame.widthProperty(), mascot.managedProperty()));
        dock.maxWidthProperty().bind(dock.prefWidthProperty());
        dock.minWidthProperty().bind(dock.prefWidthProperty());
        sites = new SitesPopup(frame, dock, configuration, preferences, onSitesChanged);
        sites.showingProperty().addListener((observable, before, shown) -> {
            if (shown) {
                mascot.react(MascotReaction.SITES);
            }
        });

        var allSites = button("squares-2x2", "All sites");
        sitesButton = allSites;
        allSites.setOnAction(e -> {
            sizes.dismiss();
            options.dismiss();
            sites.open(allSites);
        });

        var mark = new Region();
        mark.setPrefSize(24, 3);
        mark.setMaxSize(24, 3);
        mark.getStyleClass().add("shell-drag-mark");

        var drag = new StackPane(mark);
        drag.setMinWidth(22);
        drag.setMaxWidth(Double.MAX_VALUE);
        drag.setAccessibleText("Drag to move; double-click to dock at the top");
        Tooltip.install(drag, ShellTooltip.create("Drag to move · Double-click to dock at the top"));
        HBox.setHgrow(drag, Priority.ALWAYS);
        controller.installDragArea(drag);

        var size = button("arrows-pointing-out", "Window size");
        sizeButton = size;
        size.getStyleClass().setAll("button", "shell-size");
        size.setPrefWidth(Region.USE_COMPUTED_SIZE);
        size.setMinWidth(Region.USE_PREF_SIZE);
        size.setOnAction(e -> {
            sites.hide();
            options.dismiss();
            sizes.toggle(size);
        });

        var more = button("ellipsis-horizontal", "Window options");
        optionsButton = more;
        more.setOnAction(e -> {
            sites.hide();
            sizes.dismiss();
            options.toggle(more);
        });

        var hide = button("chevron-up", "Hide Spark");
        hide.setOnAction(e -> onHide.run());
        getChildren().addAll(mascot, dock, allSites, drag, size, more, hide);
        setAlignment(Pos.CENTER_LEFT);
    }

    private double reservedWidth() {
        return mascot.isManaged() ? RESERVED_WIDTH : RESERVED_WIDTH - MASCOT_WIDTH;
    }

    public void closeMenus() {
        sites.hide();
        sizes.dismiss();
        options.dismiss();
        dock.cancelInteraction();
    }

    public void dispose() {
        closeMenus();
        mascot.dispose();
        dock.dispose();
    }

    public void refreshAppearance() {
        mascot.refresh();
    }

    public static Button button(String icon, String title) {
        var button = new Button();
        button.setGraphic(ShellIcon.create(icon));
        button.getStyleClass().add("shell-button");
        button.setTooltip(ShellTooltip.create(title));
        button.setAccessibleText(title);
        button.setMinSize(30, 30);
        button.setPrefSize(30, 30);
        button.setMaxHeight(30);

        return button;
    }
}
