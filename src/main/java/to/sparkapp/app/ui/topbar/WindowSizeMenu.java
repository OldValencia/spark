package to.sparkapp.app.ui.topbar;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.stage.Stage;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.windows.WindowController;

final class WindowSizeMenu extends ShellPopover {
    WindowSizeMenu(Stage frame, AppPreferences preferences, WindowController controller) {
        super("WINDOW SIZE", 276);

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

        add(new WindowSizePresets(frame, controller, this::dismiss));
        action("arrows-pointing-out", "Fill available space", controller::fill);
        action("arrow-uturn-left", "Restore manual size", controller::restoreManualSize);
        setting("arrow-up-tray", "Dock at the top", controller.dockedProperty(), controller::toggleDock);

    }
}
