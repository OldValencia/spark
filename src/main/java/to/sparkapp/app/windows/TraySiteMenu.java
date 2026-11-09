package to.sparkapp.app.windows;

import java.awt.Menu;
import java.awt.MenuItem;
import java.util.List;
import java.util.function.Consumer;

import javafx.application.Platform;
import to.sparkapp.app.config.AiConfiguration.AiConfig;

/**
 * "Open site" in the tray menu: one item per site, in the order of the tabs. Runs on the AWT
 * thread; choosing a site hands its id to JavaFX.
 */
final class TraySiteMenu extends Menu {
    private final Consumer<String> openSite;

    TraySiteMenu(Consumer<String> openSite) {
        super("Open site");
        this.openSite = openSite;
        setEnabled(false);
    }

    void showSites(List<AiConfig> sites) {
        removeAll();

        for (var site : sites) {
            var item = new MenuItem(site.name());
            item.addActionListener(event -> Platform.runLater(() -> openSite.accept(site.id())));
            add(item);
        }

        setEnabled(!sites.isEmpty());
    }
}
