package to.sparkapp.app.ui.dialogs.components;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;

public class ProvidersFormButtonsPanel extends HBox {

    public ProvidersFormButtonsPanel(boolean isAddDialog, Runnable actionConfirmed, Runnable actionCancelled) {
        this.setAlignment(Pos.CENTER_RIGHT);
        this.setSpacing(8);
        this.setPadding(new Insets(16, 0, 0, 0));

        var cancelBtn = createButton("Cancel", false);
        cancelBtn.setOnAction(e -> {
            if (actionCancelled != null) {
                actionCancelled.run();
            }
        });

        var saveBtn = createButton(isAddDialog ? "Add" : "Save", true);
        saveBtn.setOnAction(e -> {
            if (actionConfirmed != null) {
                actionConfirmed.run();
            }
        });

        this.getChildren().addAll(cancelBtn, saveBtn);
    }

    private Button createButton(String text, boolean primary) {
        var button = new Button(text);
        button.getStyleClass().add(primary ? "primary-button" : "quiet-button");
        if (primary) button.setDefaultButton(true); else button.setCancelButton(true);
        return button;
    }
}
