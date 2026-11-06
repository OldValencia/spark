package to.sparkapp.app.ui.settings.components;

import javafx.stage.Stage;
import to.sparkapp.app.config.AiConfiguration;
import to.sparkapp.app.config.CustomAiProvidersManager;
import to.sparkapp.app.ui.Theme;
import to.sparkapp.app.ui.dialogs.ProviderEditDialog;
import to.sparkapp.app.utils.FrameUtils;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.Consumer;

public class ProvidersManagementPanel extends VBox {

    private static final int HEADER_GAP = 8;

    private final CustomAiProvidersManager providersManager;
    private final Consumer<Void> onProvidersChanged;
    private final SettingsCard listCard;

    public ProvidersManagementPanel(CustomAiProvidersManager providersManager,
                                    Consumer<Void> onProvidersChanged,
                                    Runnable onResetToDefaults) {
        this.providersManager = providersManager;
        this.onProvidersChanged = onProvidersChanged;

        this.setSpacing(HEADER_GAP);
        this.setMaxWidth(Double.MAX_VALUE);

        var resetButton = new ProvidersListTextButton("Reset", Theme.TEXT_TERTIARY, Theme.DANGER,
                onResetToDefaults);
        var addButton = new ProvidersListTextButton("+ Add", Theme.ACCENT, Theme.ACCENT,
                this::openAddDialog);

        this.getChildren().add(new SettingsSection("AI Providers", resetButton, addButton));

        listCard = new SettingsCard();
        this.getChildren().add(listCard);

        refreshProvidersList();
    }

    private void refreshProvidersList() {
        listCard.clearRows();
        var allProviders = providersManager.loadProviders();

        if (allProviders.isEmpty()) {
            listCard.addRow(new ProvidersEmptyListLabel());
        } else {
            fillProviderList(allProviders);
        }
    }

    private void fillProviderList(List<AiConfiguration.AiConfig> allProviders) {
        for (var provider : allProviders) {
            listCard.addRow(new ProviderListItem(
                    provider,
                    () -> openEditDialog(provider),
                    () -> confirmAndDelete(provider)
            ));
        }
    }

    private void openAddDialog() {
        var owner = FrameUtils.getOwnerStage(this);
        var dialog = new ProviderEditDialog(owner, null);
        dialog.showAndWait();

        if (dialog.isConfirmed()) {
            var name = dialog.getProviderName();
            var url = dialog.getProviderUrl();
            var color = dialog.getProviderColor();
            executeAsyncOp(() -> providersManager.addCustomProvider(name, url, color));
        }
    }

    private void openEditDialog(AiConfiguration.AiConfig provider) {
        var owner = FrameUtils.getOwnerStage(this);
        var dialog = new ProviderEditDialog(owner, provider);
        dialog.showAndWait();

        if (dialog.isConfirmed()) {
            var name = dialog.getProviderName();
            var url = dialog.getProviderUrl();
            var color = dialog.getProviderColor();
            var auto = dialog.isUseIconColor();
            var manual = dialog.isManualColor();
            executeAsyncOp(() -> providersManager.updateProvider(provider.id(), name, url, color, auto, manual));
        }
    }

    private void confirmAndDelete(AiConfiguration.AiConfig provider) {
        var alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Deletion");
        alert.setHeaderText(null);
        alert.setContentText("Are you sure you want to delete \"" + provider.name() + "\"?");

        var stage = (Stage) alert.getDialogPane().getScene().getWindow();
        stage.setAlwaysOnTop(true);

        var result = alert.showAndWait();
        if (result.isPresent()) {
            if (result.get() == ButtonType.OK) {
                providersManager.deleteProvider(provider.id());
                refreshProvidersList();
                if (onProvidersChanged != null) {
                    onProvidersChanged.accept(null);
                }
            }
        }
    }

    private void executeAsyncOp(Runnable backgroundAction) {
        new Thread(() -> {
            backgroundAction.run();
            Platform.runLater(() -> {
                refreshProvidersList();
                if (onProvidersChanged != null) {
                    onProvidersChanged.accept(null);
                }
            });
        }).start();
    }
}
