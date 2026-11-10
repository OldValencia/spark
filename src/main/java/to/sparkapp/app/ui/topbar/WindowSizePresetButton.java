package to.sparkapp.app.ui.topbar;

import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.PseudoClass;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SplitMenuButton;
import javafx.stage.Stage;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.config.WindowSizePreset;
import to.sparkapp.app.config.WindowSizePreset.Slot;
import to.sparkapp.app.ui.dialogs.PresetNameDialog;
import to.sparkapp.app.ui.topbar.components.ShellTooltip;
import to.sparkapp.app.windows.WindowController;

final class WindowSizePresetButton extends SplitMenuButton {
    private static final PseudoClass CURRENT = PseudoClass.getPseudoClass("current");
    private final Stage frame;
    private final AppPreferences preferences;
    private final Runnable dismiss;
    private final Slot slot;
    private final SimpleObjectProperty<WindowSizePreset> preset = new SimpleObjectProperty<>();

    WindowSizePresetButton(Stage frame, AppPreferences preferences, WindowController controller, Runnable dismiss,
                           Slot slot) {
        this.frame = frame;
        this.preferences = preferences;
        this.dismiss = dismiss;
        this.slot = slot;
        getStyleClass().add("size-preset");
        setMinWidth(0);
        setPrefWidth(0);
        setMaxWidth(Double.MAX_VALUE);
        setMnemonicParsing(false);
        getItems().addAll(action("Save current size", this::saveCurrentSize), action("Rename", this::rename),
                action("Reset to default", this::reset));
        setOnAction(event -> {
            dismiss.run();
            controller.preset(slot);
        });
        refresh();

        var matches = Bindings.createBooleanBinding(() -> Math.round(frame.getWidth()) == preset.get().width() &&
                        Math.round(frame.getHeight()) == preset.get().height(),
                frame.widthProperty(), frame.heightProperty(), preset);
        getProperties().put("sizeMatch", matches);
        matches.addListener((observable, previous, active) -> pseudoClassStateChanged(CURRENT, active));
        pseudoClassStateChanged(CURRENT, matches.get());
    }

    private MenuItem action(String label, Runnable command) {
        var item = new MenuItem(label);
        item.setOnAction(event -> command.run());

        return item;
    }

    private void refresh() {
        var size = preferences.getWindowSizePreset(slot);
        preset.set(size);
        setText(size.name());
        setAccessibleText(size.name() + ", " + size.width() + " by " + size.height() + ", size preset");
        setTooltip(ShellTooltip.create(size.name() + " · " + size.width() + " × " + size.height() +
                "\nClick to apply. Use the arrow to save, rename, or reset."));
    }

    private void saveCurrentSize() {
        preferences.setWindowSizePreset(slot, new WindowSizePreset(preset.get().name(),
                (int) Math.round(frame.getWidth()), (int) Math.round(frame.getHeight())));
        refresh();
    }

    private void reset() {
        preferences.resetWindowSizePreset(slot);
        refresh();
    }

    private void rename() {
        dismiss.run();
        var dialog = new PresetNameDialog(frame, preset.get());
        dialog.showNameAndWait().ifPresent(name -> {
            var size = preset.get();
            preferences.setWindowSizePreset(slot, new WindowSizePreset(name, size.width(), size.height()));
            refresh();
        });
    }
}
