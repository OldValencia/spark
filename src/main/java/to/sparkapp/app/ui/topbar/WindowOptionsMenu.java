package to.sparkapp.app.ui.topbar;

import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.ui.topbar.components.ShellTooltip;
import to.sparkapp.app.utils.SystemUtils;

/** App-wide actions. Actions on one site are in the menu of its tab. */
final class WindowOptionsMenu extends ShellPopover {
    WindowOptionsMenu(AppPreferences preferences, Runnable onSettings) {
        this(preferences, onSettings, () -> {});
    }

    WindowOptionsMenu(AppPreferences preferences, Runnable onSettings, Runnable onExtensions) {
        super("WINDOW OPTIONS", 258, preferences::isReducedMotion);
        action("cog-6-tooth", "Settings", onSettings);
        var extensions = action("squares-2x2", "Extensions", onExtensions);
        extensions.setDisable(!SystemUtils.isWindows());

        if (extensions.isDisabled()) {
            extensions.setTooltip(ShellTooltip.create("Extensions are available on Windows only."));
        }
    }
}
