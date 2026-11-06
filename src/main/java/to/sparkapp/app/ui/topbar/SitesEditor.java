package to.sparkapp.app.ui.topbar;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import to.sparkapp.app.config.AiConfiguration;
import to.sparkapp.app.config.AiConfiguration.AiConfig;
import to.sparkapp.app.ui.dialogs.ProviderEditDialog;

@RequiredArgsConstructor
public final class SitesEditor {
    private final Stage owner;
    private final AiConfiguration configuration;
    private final Runnable onChanged;

    public void edit(AiConfig site) {
        var dialog = new ProviderEditDialog(owner, site);
        dialog.showAndWait();

        if (!dialog.isConfirmed()) {
            return;
        }

        var request = SiteEditRequest.from(site, dialog);

        if (needsAddressConfirmation(request) && !confirmAddressChange()) {
            return;
        }

        Thread.ofPlatform().daemon().name("site-save").start(() -> save(request));
    }

    private boolean needsAddressConfirmation(SiteEditRequest request) {
        return !request.deleted() && request.original() != null && !request.original().url().equals(request.url());
    }

    private boolean confirmAddressChange() {
        var warning = new Alert(Alert.AlertType.CONFIRMATION,
                "Changing the address replaces this site's page. Its unsent text will be lost.", ButtonType.CANCEL,
                ButtonType.OK);
        warning.initOwner(owner);
        warning.setHeaderText("Change site address?");

        return warning.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private void save(SiteEditRequest request) {
        try {
            var manager = configuration.getCustomProvidersManager();
            var original = request.original();

            if (request.deleted() && original != null) {
                manager.deleteProvider(original.id());
            } else if (original == null) {
                manager.addCustomProvider(request.name(), request.url(), request.color());
            } else {
                manager.updateProvider(original.id(), request.name(), request.url(), request.color(),
                        request.useIconColor(), request.manualColor());
            }

            Platform.runLater(onChanged);
        } catch (RuntimeException error) {
            Platform.runLater(this::showSaveError);
        }
    }

    private void showSaveError() {
        var alert = new Alert(Alert.AlertType.ERROR, "Could not save this site. Please try again.");
        alert.initOwner(owner);
        alert.show();
    }
}
