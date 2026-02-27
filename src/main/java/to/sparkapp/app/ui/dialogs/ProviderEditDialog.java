package to.sparkapp.app.ui.dialogs;

import to.sparkapp.app.config.AiConfiguration;
import to.sparkapp.app.ui.dialogs.components.ProviderFormPanel;
import to.sparkapp.app.ui.dialogs.components.ProviderMainPanel;
import to.sparkapp.app.ui.dialogs.components.ProviderTitleLabel;
import to.sparkapp.app.ui.dialogs.components.ProvidersFormButtonsPanel;
import to.sparkapp.app.utils.SystemUtils;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import lombok.Getter;

public class ProviderEditDialog extends Stage {

    private final ProviderFormPanel providerFormPanel;

    @Getter
    private boolean confirmed = false;

    public ProviderEditDialog(Window owner, AiConfiguration.AiConfig provider) {
        this.initOwner(owner);
        this.initModality(Modality.WINDOW_MODAL);
        this.initStyle(StageStyle.TRANSPARENT);

        var mainPanel = new ProviderMainPanel();
        mainPanel.setTop(new ProviderTitleLabel(provider == null));

        providerFormPanel = new ProviderFormPanel(provider);
        mainPanel.setCenter(providerFormPanel);

        mainPanel.setBottom(buildButtonsPanel(provider));

        double width = 420;
        double height = SystemUtils.isWindows() ? 330 : 290;

        var scene = new Scene(mainPanel, width, height, Color.TRANSPARENT);
        this.setScene(scene);
    }

    private ProvidersFormButtonsPanel buildButtonsPanel(AiConfiguration.AiConfig provider) {
        Runnable actionConfirmed = () -> {
            if (validateForm()) {
                confirmed = true;
                this.close();
            }
        };
        Runnable actionCancelled = () -> {
            confirmed = false;
            this.close();
        };

        return new ProvidersFormButtonsPanel(provider == null, actionConfirmed, actionCancelled);
    }

    private boolean validateForm() {
        var name = getProviderName();
        var url = getProviderUrl();

        if (name.isEmpty()) {
            showValidationError("Please enter a provider name");
            return false;
        }

        if (!url.startsWith("http")) {
            showValidationError("Please enter a valid URL (starting with http:// or https://)");
            return false;
        }

        return true;
    }

    private void showValidationError(String message) {
        var alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(this);
        alert.setTitle("Validation Error");
        alert.setHeaderText(null);
        alert.setContentText(message);

        var stage = (Stage) alert.getDialogPane().getScene().getWindow();
        stage.setAlwaysOnTop(true);

        alert.showAndWait();
    }

    public String getProviderName() {
        return providerFormPanel.getNameFieldValue();
    }

    public String getProviderUrl() {
        return providerFormPanel.getUrlFieldValue();
    }
}
