package to.sparkapp.app.ui.topbar;

import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.config.WindowSizePreset.Slot;
import to.sparkapp.app.windows.WindowController;

final class WindowSizePresets extends HBox {
    WindowSizePresets(Stage frame, AppPreferences preferences, WindowController controller, Runnable dismiss) {
        super(6);

        for (var slot : Slot.values()) {
            var button = new WindowSizePresetButton(frame, preferences, controller, dismiss, slot);
            HBox.setHgrow(button, Priority.ALWAYS);
            getChildren().add(button);
        }
    }
}
