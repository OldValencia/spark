package io.loom.app.ui.settings.components;

import io.loom.app.config.AiConfiguration;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;

public class ResetProvidersPanel extends HBox {

    private static final Color RESET_BUTTON_COLOR = Color.rgb(255, 94, 91);
    private final AiConfiguration aiConfiguration;
    private final Runnable onProvidersChanged;

    public ResetProvidersPanel(AiConfiguration aiConfiguration, Runnable onProvidersChanged) {
        this.aiConfiguration = aiConfiguration;
        this.onProvidersChanged = onProvidersChanged;

        this.setAlignment(Pos.CENTER_LEFT);
        this.setMaxWidth(Double.MAX_VALUE);

        var resetButton = new ColorfulButton(
                "Reset Providers to Default",
                RESET_BUTTON_COLOR,
                this::handleResetAction
        );

        this.getChildren().add(resetButton);
    }

    private void handleResetAction() {
        var alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Reset Configuration");
        alert.setHeaderText(null);
        alert.setContentText("Are you sure? This will delete all custom providers and icons.");

        var result = alert.showAndWait();
        if (result.isPresent()) {
            if (result.get() == ButtonType.OK) {
                aiConfiguration.resetToDefaults();
                if (onProvidersChanged != null) {
                    onProvidersChanged.run();
                }
            }
        }
    }
}
