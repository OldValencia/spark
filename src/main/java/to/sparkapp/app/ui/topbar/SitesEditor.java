package to.sparkapp.app.ui.topbar;

import java.util.function.Consumer;

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

        Thread.ofPlatform().daemon().name("site-save").start(() -> save(request, id -> {
        }));
    }

    /**
     * Opens the dialog for a new site with its name and address filled in.
     *
     * @param added receives the new site's id on the JavaFX thread, before the sites are reloaded
     */
    public void add(String name, String url, Consumer<String> added) {
        var dialog = new ProviderEditDialog(owner, null);
        dialog.prefill(name, url);
        dialog.showAndWait();

        if (dialog.isConfirmed()) {
            var request = SiteEditRequest.from(null, dialog);
            Thread.ofPlatform().daemon().name("site-save").start(() -> save(request, added));
        }
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

    private void save(SiteEditRequest request, Consumer<String> added) {
        try {
            var manager = configuration.getCustomProvidersManager();
            var original = request.original();

            if (request.deleted() && original != null) {
                manager.deleteProvider(original.id());
            } else if (original == null) {
                var id = manager.addCustomProvider(request.name(), request.url(), request.color());
                Platform.runLater(() -> added.accept(id));
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
