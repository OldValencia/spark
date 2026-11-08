package to.sparkapp.app.ui.extensions;

import java.util.function.Supplier;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import to.sparkapp.app.browser.ExtensionPopup;
import to.sparkapp.app.browser.WebviewManager;
import to.sparkapp.app.extensions.ExtensionEntry;
import to.sparkapp.app.extensions.ExtensionManager;
import to.sparkapp.app.ui.topbar.components.ShellIcon;
import to.sparkapp.app.ui.topbar.components.ShellTooltip;

final class ExtensionRow extends VBox {
    private static final int ICON_SIZE = 32;

    private final ExtensionEntry entry;
    private final Supplier<WebviewManager> browser;
    private final Label openError = new Label();
    private long lastPressAt = Long.MIN_VALUE / 2;

    /** A non-null available version adds an Update button. */
    ExtensionRow(ExtensionEntry entry, String availableUpdate, Supplier<WebviewManager> browser, ExtensionRowActions actions) {
        super(10);
        this.entry = entry;
        this.browser = browser;
        getStyleClass().add("extension-card");

        var enabled = new CheckBox("Enabled");
        enabled.setSelected(entry.enabled());
        enabled.setDisable(!entry.installed());
        enabled.setOnAction(event -> actions.setEnabled(entry, enabled.isSelected()));

        var heading = new HBox(12, popupButton(), description(), enabled);
        heading.setAlignment(Pos.CENTER_LEFT);

        var remove = new Button("Remove");
        remove.getStyleClass().add("quiet-button");
        remove.setOnAction(event -> actions.remove(entry));

        var buttons = new HBox(8, settingsButton(), remove);
        if (availableUpdate != null && entry.installed()) {
            var update = new Button("Update to " + availableUpdate);
            update.getStyleClass().add("quiet-button");
            update.setOnAction(event -> actions.update(entry));
            buttons.getChildren().add(update);
        }
        getChildren().addAll(heading, buttons);
        addErrorLabels();
    }

    private Button popupButton() {
        var manifest = entry.manifest();
        var button = new Button();
        button.getStyleClass().add("extension-icon");
        button.setGraphic(icon());
        button.setAccessibleText("Open " + manifest.name());

        var hasPopup = !manifest.popup().isBlank();
        var tooltip = hasPopup ? "Open " + manifest.name() : "This extension does not declare a popup";
        button.setTooltip(ShellTooltip.create(tooltip));
        button.setDisable(!isUsable() || !hasPopup);
        button.setOnAction(event -> toggle(button, manifest.popup()));
        rememberPresses(button);

        return button;
    }

    private Node icon() {
        var path = entry.manifest().icon();
        if (path != null) {
            var image = new Image(path.toUri().toString(), ICON_SIZE, ICON_SIZE, true, true);
            if (!image.isError()) {
                return new ImageView(image);
            }
        }

        return ShellIcon.create("squares-2x2");
    }

    private VBox description() {
        var manifest = entry.manifest();
        var name = new Label(manifest.name());
        name.getStyleClass().add("setting-title");
        name.setWrapText(true);

        var status = entry.error() != null ? "Could not load" : entry.enabled() ? "Enabled" : "Disabled";
        var detail = new Label(manifest.version() + " · " + status);
        detail.getStyleClass().add("extension-detail");

        var text = new VBox(4, name, detail);
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);

        return text;
    }

    /** Opens the options page, or the popup when the extension keeps its settings there. */
    private Button settingsButton() {
        var manifest = entry.manifest();
        var page = manifest.options().isBlank() ? manifest.popup() : manifest.options();
        var button = new Button("Settings");
        button.getStyleClass().add("quiet-button");
        button.setDisable(!isUsable() || page.isBlank());
        button.setOnAction(event -> toggle(button, page));
        rememberPresses(button);

        return button;
    }

    private void addErrorLabels() {
        if (entry.error() != null) {
            var error = errorLabel(entry.error());
            getChildren().add(error);
        }

        openError.getStyleClass().add("field-error");
        openError.setWrapText(true);
        openError.setVisible(false);
        openError.managedProperty().bind(openError.visibleProperty());
        getChildren().add(openError);
    }

    private boolean isUsable() {
        return entry.installed() && entry.enabled();
    }

    /** The press time distinguishes a click that already dismissed this popup from a fresh click. */
    private void rememberPresses(Button button) {
        button.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> lastPressAt = System.nanoTime());
    }

    /** Clicking the button of an open popup closes it instead of opening it again. */
    private void toggle(Node anchor, String page) {
        if (!ExtensionPopup.closeIfShowing(entry.id(), page, lastPressAt)) {
            open(anchor, page);
        }
    }

    private void open(Node anchor, String page) {
        var active = browser.get();
        if (active == null) {
            return;
        }

        openError.setVisible(false);
        var windowId = ExtensionManager.instance().whenSynchronized()
                .thenCompose(ignored -> active.extensionContextWindow());
        windowId.whenComplete((id, error) -> Platform.runLater(() -> {
            if (error != null) {
                openError.setText("Could not connect the extension to the current site. " + error.getMessage());
                openError.setVisible(true);
            } else if (anchor.getScene() != null && anchor.getScene().getWindow().isShowing()) {
                new ExtensionPopup().openAnchored(anchor, entry.manifest().name(), entry.id(), page, id);
            }
        }));
    }

    private static Label errorLabel(String text) {
        var label = new Label(text);
        label.getStyleClass().add("field-error");
        label.setWrapText(true);

        return label;
    }
}
