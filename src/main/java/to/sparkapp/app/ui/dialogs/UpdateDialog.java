package to.sparkapp.app.ui.dialogs;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;
import javafx.stage.Window;

public class UpdateDialog {

    public static boolean show(String version, Window owner) {
        var alert = new Alert(Alert.AlertType.CONFIRMATION);
        if (owner != null) {
            alert.initOwner(owner);
        }
        alert.setTitle("Update Available");
        alert.setHeaderText(null);
        alert.setContentText("New version " + version + " is available! Download now?");

        alert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);

        // Force the dialog to be on top of the AlwaysOnTop main window
        var stage = (Stage) alert.getDialogPane().getScene().getWindow();
        stage.setAlwaysOnTop(true);

        var result = alert.showAndWait();
        return result.filter(buttonType -> buttonType == ButtonType.YES).isPresent();
    }
}
