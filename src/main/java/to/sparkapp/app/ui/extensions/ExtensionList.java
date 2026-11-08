package to.sparkapp.app.ui.extensions;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import javafx.scene.layout.VBox;
import to.sparkapp.app.browser.WebviewManager;
import to.sparkapp.app.extensions.ExtensionEntry;

/** The extension rows, rebuilt only when the entries or the available updates change. */
final class ExtensionList extends VBox {
    private final Supplier<WebviewManager> browser;
    private final ExtensionRowActions actions;
    private final Map<Path, String> updates = new HashMap<>();
    private List<ExtensionEntry> entries = List.of();

    ExtensionList(Supplier<WebviewManager> browser, ExtensionRowActions actions) {
        super(10);
        this.browser = browser;
        this.actions = actions;
    }

    void show(List<ExtensionEntry> shown) {
        if (shown.equals(entries) && !getChildren().isEmpty()) {
            return;
        }

        entries = shown;
        rebuild();
    }

    /** Replaces the known updates; folders that are no longer listed are ignored. */
    void showUpdates(Map<Path, String> available) {
        updates.clear();
        updates.putAll(available);
        rebuild();
    }

    void forgetUpdate(Path directory) {
        if (updates.remove(directory) != null) {
            rebuild();
        }
    }

    private void rebuild() {
        var rows = entries.stream()
                .sorted(Comparator.comparing(entry -> entry.manifest().name(), String.CASE_INSENSITIVE_ORDER))
                .map(entry -> new ExtensionRow(entry, updates.get(entry.manifest().directory()), browser, actions))
                .toList();
        getChildren().setAll(rows);
    }
}
