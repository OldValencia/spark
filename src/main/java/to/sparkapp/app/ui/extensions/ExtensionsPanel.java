package to.sparkapp.app.ui.extensions;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import to.sparkapp.app.browser.ExtensionPopup;
import to.sparkapp.app.browser.WebviewManager;
import to.sparkapp.app.config.AppPaths;
import to.sparkapp.app.extensions.ExtensionEntry;
import to.sparkapp.app.extensions.ExtensionManager;
import to.sparkapp.app.utils.SystemUtils;

/**
 * Shows the last known extension list immediately and refreshes it in the background.
 * Only the most recently started operation may update the panel.
 */
public final class ExtensionsPanel extends VBox implements ExtensionRowActions {
    private static final String HELP = "Install from the Chrome Web Store by pasting a store link or extension ID, "
            + "import a ZIP or CRX, or add an unpacked extension folder and press Refresh. "
            + "Importing a newer package of an installed extension updates it in place. "
            + "Reload an open site after installing. Active-site support is experimental and may vary by extension.";

    private final Supplier<WebviewManager> browser;
    private final ExtensionManager manager = ExtensionManager.instance();
    private final ExtensionList list;
    private final ExtensionLoadingStatus status = new ExtensionLoadingStatus();
    private final FlowPane actions;
    private long generation;
    private boolean showProgress;

    public ExtensionsPanel(Supplier<WebviewManager> browser) {
        super(14);
        this.browser = browser;
        list = new ExtensionList(browser, this);
        getStyleClass().add("extensions-panel");
        setPadding(new Insets(16, 20, 20, 20));

        actions = new FlowPane(8, 8, button("Refresh", this::refresh), button("Check for updates", this::checkUpdates),
                button("Import extensions", this::importExtensions), button("Open extensions folder", this::openFolder));
        actions.setDisable(!SystemUtils.isWindows());
        actions.setStyle("-fx-alignment:center-left;");

        var help = new Label(HELP);
        help.setWrapText(true);
        help.getStyleClass().add("field-hint");
        var store = new ChromeWebStoreField(this::installFromStore, status::setText);
        store.disableProperty().bind(actions.disableProperty());
        getChildren().addAll(actions, store, help, status, list);

        manager.addProgressListener(progress -> Platform.runLater(() -> {
            if (showProgress) {
                status.setText(progress.message());
            }
        }));
        prepareBrowserWhileShowing();
    }

    public void refresh() {
        if (!SystemUtils.isWindows()) {
            status.setText("Extensions are currently supported on Windows only.");
            actions.setDisable(true);
            return;
        }

        var cached = manager.cachedEntries();
        cached.ifPresent(this::showEntries);
        run(manager.refresh(browser.get()), cached.isPresent(), this::showEntries);
    }

    @Override
    public void setEnabled(ExtensionEntry entry, boolean enabled) {
        run(manager.setEnabled(browser.get(), entry, enabled), false, this::showEntries);
    }

    @Override
    public void remove(ExtensionEntry entry) {
        run(manager.remove(browser.get(), entry), false, this::showEntries);
    }

    @Override
    public void update(ExtensionEntry entry) {
        run(manager.update(browser.get(), entry), false, entries -> {
            list.forgetUpdate(entry.manifest().directory());
            showEntries(entries);
        });
    }

    /** A spare popup browser exists only while this window is visible. */
    private void prepareBrowserWhileShowing() {
        sceneProperty().flatMap(Scene::windowProperty).flatMap(Window::showingProperty).orElse(false)
                .addListener((observable, wasShowing, showing) -> {
                    if (showing) {
                        ExtensionPopup.prepareForOpening();
                    } else {
                        ExtensionPopup.stopPreparing();
                    }
                });
    }

    private void installFromStore(String storeId) {
        run(manager.installFromStore(browser.get(), storeId), false, this::showEntries);
    }

    private void checkUpdates() {
        run(manager.checkUpdates(browser.get()), false, found -> {
            list.showUpdates(found);
            status.setText(updateSummary(found));
        });
    }

    private void importExtensions() {
        var chooser = new FileChooser();
        chooser.setTitle("Import extensions");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Extension packages", "*.zip", "*.crx"));
        var files = chooser.showOpenMultipleDialog(getScene().getWindow());
        if (files == null || files.isEmpty()) {
            return;
        }

        var active = browser.get();
        var task = CompletableFuture.<List<ExtensionEntry>>completedFuture(List.of());
        for (var file : files) {
            task = task.thenCompose(ignored -> manager.importArchive(active, file.toPath()));
        }
        run(task, false, this::showEntries);
    }

    private void openFolder() {
        try {
            var folder = new File(AppPaths.DATA_DIR, "extensions");
            folder.mkdirs();
            Desktop.getDesktop().open(folder);
        } catch (Exception error) {
            status.setText(ExtensionManager.message(error));
        }
    }

    /** A quiet run keeps the current list usable while it is being verified. */
    private <T> void run(CompletableFuture<T> task, boolean quiet, Consumer<T> onSuccess) {
        var current = ++generation;
        showProgress = !quiet;
        status.setBusy(true);
        if (!quiet) {
            status.setText("Loading extensions…");
            setInteractive(false);
        }

        task.whenComplete((result, error) -> Platform.runLater(() -> {
            if (current != generation) {
                return;
            }

            showProgress = false;
            status.setBusy(false);
            setInteractive(true);
            if (error != null) {
                status.setText(ExtensionManager.message(error));
            } else {
                onSuccess.accept(result);
            }
        }));
    }

    private void showEntries(List<ExtensionEntry> entries) {
        status.setText(summary(entries));
        list.show(entries);
    }

    private void setInteractive(boolean interactive) {
        actions.setDisable(!interactive);
        list.setDisable(!interactive);
    }

    private Button button(String text, Runnable action) {
        var button = new Button(text);
        button.getStyleClass().add("quiet-button");
        button.setOnAction(event -> action.run());
        return button;
    }

    private static String summary(List<ExtensionEntry> entries) {
        if (entries.isEmpty()) {
            return "No extensions yet. Import a package to get started.";
        }

        var count = entries.size() == 1 ? "1 extension. " : entries.size() + " extensions. ";
        return count + "Click an icon to open its interface.";
    }

    private static String updateSummary(Map<Path, String> found) {
        if (found.isEmpty()) {
            return "Extensions with an update service are up to date. Others update by importing a newer package.";
        }

        return found.size() == 1 ? "1 update is available." : found.size() + " updates are available.";
    }
}
