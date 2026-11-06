package to.sparkapp.app.ui.topbar;

import to.sparkapp.app.ui.topbar.components.ShellTooltip;

import javafx.beans.binding.Bindings;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import to.sparkapp.app.config.AiConfiguration;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.ui.topbar.components.AiDock;
import to.sparkapp.app.ui.topbar.components.ShellIcon;
import to.sparkapp.app.ui.webview.FxWebViewPane;
import to.sparkapp.app.windows.SettingsWindow;
import to.sparkapp.app.windows.WindowController;

public class TopBarArea extends HBox {
    public static final int HEIGHT = 52;
    private final AiDock dock;
    private final SitesPopup sites;
    private final ShellPopover sizes = new ShellPopover("WINDOW SIZE", 276);
    private final ShellPopover options = new ShellPopover("WINDOW OPTIONS", 258);

    public TopBarArea(AiConfiguration configuration, FxWebViewPane browser, Stage frame,
                      SettingsWindow settings, AppPreferences preferences, Runnable onSettings,
                      Runnable onHide, WindowController controller, Runnable onSitesChanged) {
        getStyleClass().add("shell-bar");
        setMinHeight(HEIGHT); setPrefHeight(HEIGHT); setMaxHeight(HEIGHT);
        var mascot = new Region(); mascot.setMinSize(30, 30); mascot.setPrefSize(30, 30); mascot.setMaxSize(30, 30);
        mascot.setMouseTransparent(true);
        dock = new AiDock(configuration.getConfigurations(), browser, preferences, browser.getActiveSiteId());
        double dockBudget = dock.desiredWidth();
        dock.prefWidthProperty().bind(Bindings.createDoubleBinding(
                () -> Math.max(36, Math.min(dockBudget, frame.getWidth() - 240)), frame.widthProperty()));
        dock.maxWidthProperty().bind(dock.prefWidthProperty());
        dock.minWidthProperty().bind(dock.prefWidthProperty());
        sites = new SitesPopup(frame, dock, configuration, preferences, onSitesChanged);
        var allSites = button("squares-2x2", "All sites");
        allSites.setOnAction(e -> { sizes.dismiss(); options.dismiss(); sites.open(allSites); });
        var mark = new Region(); mark.setPrefSize(24, 3); mark.setMaxSize(24, 3); mark.getStyleClass().add("shell-drag-mark");
        var drag = new StackPane(mark); drag.setMinWidth(22); drag.setMaxWidth(Double.MAX_VALUE);
        drag.setAccessibleText("Drag to move; double-click to dock at the top");
        Tooltip.install(drag, ShellTooltip.create("Drag to move · Double-click to dock at the top"));
        HBox.setHgrow(drag, Priority.ALWAYS);
        controller.installDragArea(drag);
        var size = button("arrows-pointing-out", "Window size");
        size.getStyleClass().setAll("button", "shell-size");
        size.setPrefWidth(Region.USE_COMPUTED_SIZE); size.setMinWidth(Region.USE_PREF_SIZE);
        var pinned = new SimpleBooleanProperty(preferences.isAlwaysOnTop());
        sizes.setting("pin", "Always on top", pinned, () -> {
            pinned.set(!pinned.get()); preferences.setAlwaysOnTop(pinned.get()); frame.setAlwaysOnTop(pinned.get());
        }, true);
        var autoHide = new SimpleBooleanProperty(preferences.isAutoHide());
        sizes.setting("eye-slash", "Hide on outside click", autoHide, () -> { autoHide.set(!autoHide.get()); preferences.setAutoHide(autoHide.get()); });
        var presets = new HBox(6);
        for (String preset : new String[]{"S", "M", "L"}) {
            var choice = new Button(preset); choice.getStyleClass().add("size-preset");
            choice.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(choice, Priority.ALWAYS);
            int expectedWidth = preset.equals("S") ? 700 : preset.equals("M") ? 860 : 1120;
            int expectedHeight = preset.equals("S") ? 580 : preset.equals("M") ? 700 : 820;
            var matches = Bindings.createBooleanBinding(() -> Math.round(frame.getWidth()) == expectedWidth
                    && Math.round(frame.getHeight()) == expectedHeight, frame.widthProperty(), frame.heightProperty());
            choice.getProperties().put("sizeMatch", matches);
            var current = PseudoClass.getPseudoClass("current");
            choice.pseudoClassStateChanged(current, matches.get());
            matches.addListener((o, before, active) -> choice.pseudoClassStateChanged(current, active));
            choice.setOnAction(e -> { sizes.dismiss();
                switch (preset) {
                    case "S" -> controller.preset(700, 580);
                    case "M" -> controller.preset(860, 700);
                    default -> controller.preset(1120, 820);
                }
            }); presets.getChildren().add(choice);
        }
        sizes.add(presets);
        sizes.action("arrows-pointing-out", "Fill available space", controller::fill);
        sizes.action("arrow-uturn-left", "Restore manual size", controller::restoreManualSize);
        sizes.setting("arrow-up-tray", "Dock at the top", controller.dockedProperty(), controller::toggleDock);
        var hover = new SimpleBooleanProperty(preferences.isTopEdgeReveal());
        var hoverRow = sizes.setting("bars-2", "Open on top-edge hover", hover, () -> { hover.set(!hover.get()); preferences.setTopEdgeReveal(hover.get()); });
        hoverRow.setTooltip(ShellTooltip.create("Open Spark on hover when the pill is kept visible.\nWith an auto-hidden pill, hover previews the pill; click it to open Spark."));
        size.setOnAction(e -> { sites.hide(); options.dismiss(); sizes.toggle(size); });
        var more = button("ellipsis-horizontal", "Window options");
        options.action("cog-6-tooth", "Settings", onSettings);
        var pill = new SimpleBooleanProperty(preferences.isTopPill());
        var pillRow = options.setting("bars-2", "Top-edge pill", pill, () -> { pill.set(!pill.get()); preferences.setTopPill(pill.get()); controller.refreshPill(); });
        pillRow.setTooltip(ShellTooltip.create("On: keep the pill visible while Spark is hidden.\nOff: reveal the pill when hovering near the top edge."));
        var motion = new SimpleBooleanProperty(preferences.isReducedMotion());
        options.setting("bolt-slash", "Reduce motion", motion, () -> { motion.set(!motion.get()); preferences.setReducedMotion(motion.get()); });
        var zoomOut = zoomButton("−", "Zoom out\nOn the page: Ctrl + -", browser::zoomOut);
        var reset = zoomButton("100%", "Reset zoom\nOn the page: Ctrl + 0", browser::resetZoom);
        reset.getStyleClass().setAll("button", "zoom-reset"); reset.setMinWidth(52);
        var zoomIn = zoomButton("+", "Zoom in\nOn the page: Ctrl + +", browser::zoomIn);
        var zoomControls = new HBox(zoomOut, reset, zoomIn); zoomControls.getStyleClass().add("zoom-controls");
        var space = new Region(); HBox.setHgrow(space, Priority.ALWAYS);
        var zoomRow = new HBox(ShellIcon.create("magnifying-glass"), new Label("Zoom"), space, zoomControls);
        zoomRow.getStyleClass().add("zoom-row"); options.add(zoomRow);
        browser.setZoomCallback(percent -> {
            reset.setText(Math.round(percent) + "%");
            zoomOut.setDisable(!preferences.isZoomEnabled() || percent <= 58);
            zoomIn.setDisable(!preferences.isZoomEnabled() || percent >= 207);
        });
        more.setOnAction(e -> { sites.hide(); sizes.dismiss(); options.toggle(more); });
        var hide = button("chevron-up", "Hide Spark"); hide.setOnAction(e -> onHide.run());
        getChildren().addAll(mascot, dock, allSites, drag, size, more, hide);
        setAlignment(Pos.CENTER_LEFT);
    }

    public AiDock getDock() { return dock; }
    public void closeMenus() { sites.hide(); sizes.dismiss(); options.dismiss(); dock.cancelInteraction(); }
    public void dispose() { closeMenus(); dock.dispose(); }

    public static Button button(String icon, String title) {
        var button = new Button(); button.setGraphic(ShellIcon.create(icon));
        button.getStyleClass().add("shell-button"); button.setTooltip(ShellTooltip.create(title)); button.setAccessibleText(title);
        button.setMinSize(30, 30); button.setPrefSize(30, 30); button.setMaxHeight(30);
        return button;
    }

    private static Button zoomButton(String text, String hint, Runnable command) {
        var button = new Button(text); button.getStyleClass().add("zoom-step");
        button.setMinSize(28, 28); button.setTooltip(ShellTooltip.create(hint)); button.setAccessibleText(hint.split("\n")[0]);
        button.setOnAction(e -> command.run());
        return button;
    }

}
