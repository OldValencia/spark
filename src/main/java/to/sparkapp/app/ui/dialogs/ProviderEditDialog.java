package to.sparkapp.app.ui.dialogs;

import java.util.List;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import lombok.Getter;
import to.sparkapp.app.config.AiConfiguration.AiConfig;
import to.sparkapp.app.ui.dialogs.components.ProviderFormPanel;
import to.sparkapp.app.ui.dialogs.components.ProviderMainPanel;
import to.sparkapp.app.ui.dialogs.components.ProviderRemovalButton;
import to.sparkapp.app.ui.dialogs.components.ProviderTitleLabel;
import to.sparkapp.app.ui.dialogs.components.ProvidersFormButtonsPanel;
import to.sparkapp.app.utils.UrlUtils;

public class ProviderEditDialog extends Stage {
    private final ProviderFormPanel providerFormPanel;
    private final Label errorLabel = new Label();
    @Getter
    private boolean deleted;
    @Getter
    private boolean confirmed = false;

    public ProviderEditDialog(Window owner, AiConfig provider) {
        this.initOwner(owner);
        this.initModality(Modality.WINDOW_MODAL);
        this.initStyle(StageStyle.TRANSPARENT);

        var mainPanel = new ProviderMainPanel();
        mainPanel.setTop(new ProviderTitleLabel(provider == null));
        providerFormPanel = new ProviderFormPanel(provider);
        errorLabel.getStyleClass().add("field-error");
        errorLabel.setWrapText(true);
        errorLabel.setMinHeight(18);
        mainPanel.setCenter(new VBox(6, providerFormPanel, errorLabel));
        mainPanel.setBottom(buildButtonsPanel(provider));

        double width = 420;
        double height = 410;
        // The stage is transparent, so the shadow needs padding around the panel to
        // have somewhere to fall.
        var pad = ProviderMainPanel.SHADOW_PADDING;
        var root = new StackPane(mainPanel);
        root.setPadding(new Insets(pad));
        root.setStyle("-fx-background-color: transparent;");
        root.setPickOnBounds(false);

        var scene = new Scene(root, width + pad * 2, height + pad * 2, Color.TRANSPARENT);
        scene.getStylesheets().add(getClass().getResource("/css/shell.css").toExternalForm());
        this.setScene(scene);
        setOnShown(e -> {
            var work = Screen.getScreensForRectangle(owner.getX(), owner.getY(), owner.getWidth(), owner.getHeight())
                    .stream().findFirst().orElse(Screen.getPrimary()).getVisualBounds();
            setX(Math.clamp(owner.getX() + (owner.getWidth() - getWidth()) / 2, work.getMinX(),
                    Math.max(work.getMinX(), work.getMaxX() - getWidth())));
            setY(Math.clamp(owner.getY() + (owner.getHeight() - getHeight()) / 2, work.getMinY(),
                    Math.max(work.getMinY(), work.getMaxY() - getHeight())));
        });
    }

    private ProvidersFormButtonsPanel buildButtonsPanel(AiConfig provider) {
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
        var buttons = new ProvidersFormButtonsPanel(provider == null, actionConfirmed, actionCancelled);

        if (provider != null) {
            var remove = new ProviderRemovalButton(this::confirmRemoval, errorLabel::setText);
            var space = new Region();
            HBox.setHgrow(space, Priority.ALWAYS);
            buttons.getChildren().addAll(0, List.of(remove, space));
        }

        return buttons;
    }

    private boolean validateForm() {
        var name = getProviderName();
        var url = getProviderUrl();

        if (name.isEmpty()) {
            showValidationError("Enter a name for this site.");

            return false;
        }

        if (!UrlUtils.isWebUrl(url)) {
            showValidationError("Please enter a valid URL (starting with http:// or https://)");

            return false;
        }

        return true;
    }

    private void confirmRemoval() {
        deleted = true;
        confirmed = true;
        close();
    }

    private void showValidationError(String message) {
        errorLabel.setText(message);
    }

    public String getProviderName() {
        return providerFormPanel.getNameFieldValue();
    }

    public String getProviderUrl() {
        return providerFormPanel.getUrlFieldValue();
    }

    public String getProviderColor() {
        return providerFormPanel.getColorValue();
    }

    public boolean isUseIconColor() {
        return providerFormPanel.isUseIconColor();
    }

    public boolean isManualColor() {
        return providerFormPanel.isManualColor();
    }
}
