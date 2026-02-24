package to.sparkapp.app.ui.settings.components;

import to.sparkapp.app.config.AiConfiguration;
import to.sparkapp.app.config.CustomAiProvidersManager;
import to.sparkapp.app.windows.FrameUtils;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.Consumer;

public class ProvidersManagementPanel extends VBox {

    private final CustomAiProvidersManager providersManager;
    private final Consumer<Void> onProvidersChanged;
    private final VBox listContainer;

    public ProvidersManagementPanel(CustomAiProvidersManager providersManager, Consumer<Void> onProvidersChanged) {
        this.providersManager = providersManager;
        this.onProvidersChanged = onProvidersChanged;

        this.setPadding(new Insets(0, 0, 12, 0));
        this.setSpacing(8); // Gap between header and list

        this.getChildren().add(new ProvidersListHeader(this::openAddDialog));

        listContainer = new VBox();
        listContainer.setSpacing(4);
        this.getChildren().add(listContainer);

        refreshProvidersList();
    }

    private void refreshProvidersList() {
        listContainer.getChildren().clear();
        var allProviders = providersManager.loadProviders();

        if (allProviders.isEmpty()) {
            listContainer.getChildren().add(new ProvidersEmptyListLabel());
        } else {
            fillProviderList(allProviders);
        }
    }

    private void fillProviderList(List<AiConfiguration.AiConfig> allProviders) {
        for (var provider : allProviders) {
            var itemPanel = new ProviderListItem(
                    provider,
                    () -> openEditDialog(provider),
                    () -> confirmAndDelete(provider)
            );
            listContainer.getChildren().add(itemPanel);
        }
    }

    private void openAddDialog() {
        var owner = FrameUtils.getOwnerStage(this);
        var dialog = new to.sparkapp.app.ui.dialogs.ProviderEditDialog(owner, null);
        dialog.showAndWait();

        if (dialog.isConfirmed()) {
            var name = dialog.getProviderName();
            var url = dialog.getProviderUrl();
            var color = String.format("#%06x", (int) (Math.random() * 0xFFFFFF));

            executeAsyncOp(() -> providersManager.addCustomProvider(name, url, color));
        }
    }

    private void openEditDialog(AiConfiguration.AiConfig provider) {
        var owner = FrameUtils.getOwnerStage(this);
        var dialog = new to.sparkapp.app.ui.dialogs.ProviderEditDialog(owner, provider);
        dialog.showAndWait();

        if (dialog.isConfirmed()) {
            var name = dialog.getProviderName();
            var url = dialog.getProviderUrl();

            executeAsyncOp(() -> providersManager.updateProvider(provider.id(), name, url, provider.color()));
        }
    }

    private void confirmAndDelete(AiConfiguration.AiConfig provider) {
        var alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Deletion");
        alert.setHeaderText(null);
        alert.setContentText("Are you sure you want to delete \"" + provider.name() + "\"?");

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
