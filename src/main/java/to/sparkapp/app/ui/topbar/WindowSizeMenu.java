package to.sparkapp.app.ui.topbar;

import java.util.function.Supplier;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import to.sparkapp.app.config.AiConfiguration.AiConfig;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.windows.WindowController;

final class WindowSizeMenu extends ShellPopover {
    private final WindowController controller;
    private final Supplier<AiConfig> siteOnScreen;
    private final SimpleBooleanProperty ownSize = new SimpleBooleanProperty();
    private final ShellSettingRow ownSizeRow;

    WindowSizeMenu(Stage frame, AppPreferences preferences, WindowController controller,
                   Supplier<AiConfig> siteOnScreen) {
        super("WINDOW SIZE", 276, preferences::isReducedMotion);
        this.controller = controller;
        this.siteOnScreen = siteOnScreen;

        var pinned = new SimpleBooleanProperty(preferences.isAlwaysOnTop());
        setting("pin", "Always on top", pinned, () -> {
            pinned.set(!pinned.get());
            preferences.setAlwaysOnTop(pinned.get());
            frame.setAlwaysOnTop(pinned.get());
        }, true);

        var autoHide = new SimpleBooleanProperty(preferences.isAutoHide());
        setting("eye-slash", "Hide on outside click", autoHide, () -> {
            autoHide.set(!autoHide.get());
            preferences.setAutoHide(autoHide.get());
        });

        ownSizeRow = setting("bookmark", "Remember size for this site", ownSize, this::toggleOwnSize);
        ownSizeRow.managedProperty().bind(ownSizeRow.visibleProperty());
        add(new WindowSizePresets(frame, preferences, controller, this::dismiss));
        action("arrows-pointing-out", "Fill available space", controller::fill);
        action("arrow-uturn-left", "Restore manual size", controller::restoreManualSize);
        setting("arrow-up-tray", "Dock at the top", controller.dockedProperty(), controller::toggleDock);

        addEventHandler(WindowEvent.WINDOW_SHOWING, event -> refreshOwnSize());
    }

    /** Names the site on screen, which is the one the setting applies to. */
    private void refreshOwnSize() {
        var site = siteOnScreen.get();
        ownSizeRow.setVisible(site != null);

        if (site != null) {
            ownSizeRow.setTitle("Remember size for " + site.name());
            ownSize.set(controller.hasOwnSize(site.id()));
        }
    }

    /** The window's current size becomes the site's; the next preset, fill or resize on that site replaces it. */
    private void toggleOwnSize() {
        var site = siteOnScreen.get();

        if (site != null) {
            ownSize.set(!ownSize.get());
            controller.setOwnSize(site.id(), ownSize.get());
        }
    }
}
