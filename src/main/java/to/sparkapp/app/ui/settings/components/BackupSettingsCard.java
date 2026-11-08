package to.sparkapp.app.ui.settings.components;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;

import javafx.stage.FileChooser;
import lombok.extern.slf4j.Slf4j;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.config.SettingsExport;
import to.sparkapp.app.ui.dialogs.ShellMessageDialog;
import to.sparkapp.app.utils.SystemUtils;

/**
 * "Export settings": saves the sites and preferences to a ZIP file and explains where to copy it.
 */
@Slf4j
public final class BackupSettingsCard extends SettingsCard {
    private static final String TITLE = "Export settings";

    private final AppPreferences preferences;

    public BackupSettingsCard(AppPreferences preferences) {
        this.preferences = preferences;
        var button = new AnimatedSettingsButton("Export…", this::export);
        addRow(new SettingsRow(TITLE, "Save your sites and preferences to a ZIP file", button));
    }

    private void export() {
        var target = chooseTarget();

        if (target == null) {
            return;
        }

        preferences.flush();

        try {
            new SettingsExport().writeTo(target.toPath());
            showExported(target);
        } catch (IOException | RuntimeException failure) {
            log.error("Could not export settings", failure);
            showFailure();
        }
    }

    private File chooseTarget() {
        var chooser = new FileChooser();
        chooser.setTitle(TITLE);
        chooser.setInitialFileName(SettingsExport.suggestedFileName());
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ZIP archive", "*.zip"));

        var downloads = new File(System.getProperty("user.home"), "Downloads");

        if (downloads.isDirectory()) {
            chooser.setInitialDirectory(downloads);
        }

        return chooser.showSaveDialog(getScene().getWindow());
    }

    private void showExported(File target) {
        new ShellMessageDialog(getScene().getWindow(), "Settings exported", "Done")
                .hint(target.getName() + " in " + target.getParent())
                .text("To use them on this or another PC, quit Spark and copy the files from the archive into:")
                .copyable(SettingsExport.restoreFolder())
                .hint("The same steps are in README.txt inside the archive.")
                .action("Show file", () -> showInFolder(target))
                .show();
    }

    private void showFailure() {
        new ShellMessageDialog(getScene().getWindow(), "Settings could not be exported", "OK")
                .text("Choose another folder and try again. The application log has the details.")
                .show();
    }

    private static void showInFolder(File target) {
        try {
            if (SystemUtils.isWindows()) {
                new ProcessBuilder("explorer.exe", "/select," + target.getAbsolutePath()).start();
            } else {
                Desktop.getDesktop().open(target.getParentFile());
            }
        } catch (IOException | RuntimeException failure) {
            log.warn("Could not show the exported file", failure);
        }
    }
}
