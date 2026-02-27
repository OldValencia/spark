package to.sparkapp.app.ui.dialogs.components;

import to.sparkapp.app.ui.Theme;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
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
        button.setFont(Theme.FONT_SETTINGS);
        button.setCursor(Cursor.HAND);

        var bgNormal = primary ? Theme.toHex(Theme.ACCENT) : Theme.toHex(Theme.BG_POPUP);
        var bgHover = primary ? Theme.toHex(Theme.ACCENT.brighter()) : Theme.toHex(Theme.BG_HOVER);
        var textFill = primary ? "white" : Theme.toHex(Theme.TEXT_PRIMARY);

        button.setStyle(
                "-fx-background-color: " + bgNormal + "; " +
                        "-fx-text-fill: " + textFill + "; " +
                        "-fx-padding: 10 24 10 24; " +
                        "-fx-background-radius: 4;"
        );

        // Hover effect via standard events
        button.setOnMouseEntered(e -> button.setStyle(
                "-fx-background-color: " + bgHover + "; " +
                        "-fx-text-fill: " + textFill + "; " +
                        "-fx-padding: 10 24 10 24; " +
                        "-fx-background-radius: 4;"
        ));

        button.setOnMouseExited(e -> button.setStyle(
                "-fx-background-color: " + bgNormal + "; " +
                        "-fx-text-fill: " + textFill + "; " +
                        "-fx-padding: 10 24 10 24; " +
                        "-fx-background-radius: 4;"
        ));

        return button;
    }
}
