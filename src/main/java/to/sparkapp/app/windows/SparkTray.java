package to.sparkapp.app.windows;

import java.awt.*;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import javafx.application.Platform;

import javax.imageio.ImageIO;

import lombok.extern.slf4j.Slf4j;
import to.sparkapp.app.config.AiConfiguration.AiConfig;

@Slf4j
final class SparkTray implements AutoCloseable {
    private final Runnable showApplication;
    private final Runnable exitApplication;
    private final Consumer<String> openSite;
    private volatile List<AiConfig> sites = List.of();
    private static final String TOOLTIP = "Spark";
    /** Changed on the JavaFX thread. */
    private final Set<String> updatedSites = new LinkedHashSet<>();
    private TraySiteMenu siteMenu;
    private volatile TrayIcon trayIcon;
    private Image icon;
    private Image updatedIcon;

    SparkTray(Runnable showApplication, Consumer<String> openSite, Runnable exitApplication) {
        this.showApplication = showApplication;
        this.openSite = openSite;
        this.exitApplication = exitApplication;
    }

    /** Updates "Open site" with the sites in the order of the tabs. */
    void showSites(List<AiConfig> sites) {
        this.sites = List.copyOf(sites);
        EventQueue.invokeLater(() -> {
            if (siteMenu != null) {
                siteMenu.showSites(this.sites);
            }
        });
    }

    void show() {
        if (!SystemTray.isSupported()) {
            return;
        }

        var tray = SystemTray.getSystemTray();
        var iconUrl = getClass().getResource("/app-icons/icon.png");

        if (iconUrl == null) {
            iconUrl = getClass().getResource("/app-icons/icon.ico");
        }

        Image image;

        try {
            image = ImageIO.read(iconUrl);
        } catch (IOException | IllegalArgumentException e) {
            log.error("Failed to load tray icon", e);

            return;
        }

        var popup = new PopupMenu();
        siteMenu = new TraySiteMenu(openSite);
        siteMenu.showSites(sites);
        popup.add(siteMenu);

        var showItem = new MenuItem("Show Application");
        showItem.addActionListener(e -> Platform.runLater(showApplication));
        popup.add(showItem);

        popup.addSeparator();

        var exitItem = new MenuItem("Exit Spark");
        exitItem.addActionListener(e -> Platform.runLater(exitApplication));
        popup.add(exitItem);

        icon = image;
        updatedIcon = TrayUpdateDot.onto(image);
        trayIcon = new TrayIcon(image, TOOLTIP, popup);
        trayIcon.setImageAutoSize(true);
        trayIcon.addActionListener(e -> Platform.runLater(showApplication));

        try {
            tray.add(trayIcon);
        } catch (AWTException e) {
            log.error("Failed to setup system tray", e);
        }
    }

    /** Puts a dot on the tray icon: a site changed its page while Spark was hidden. */
    void markUpdated(String siteName) {
        updatedSites.add(siteName);
        showUpdates(List.copyOf(updatedSites));
    }

    /** Removes the dot once Spark is shown, where each tab shows its own dot. */
    void clearUpdates() {
        if (!updatedSites.isEmpty()) {
            updatedSites.clear();
            showUpdates(List.of());
        }
    }

    private void showUpdates(List<String> names) {
        EventQueue.invokeLater(() -> {
            if (trayIcon == null) {
                return;
            }

            trayIcon.setImage(names.isEmpty() ? icon : updatedIcon);
            trayIcon.setToolTip(names.isEmpty() ? TOOLTIP : TOOLTIP + " · New on " + String.join(", ", names));
        });
    }

    @Override
    public void close() {
        if (trayIcon != null && SystemTray.isSupported()) {
            SystemTray.getSystemTray().remove(trayIcon);
        }
    }
}