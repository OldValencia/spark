package to.sparkapp.app.windows;

import java.awt.*;
import java.io.IOException;
import java.util.List;
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
    private TraySiteMenu siteMenu;
    private TrayIcon trayIcon;

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

        trayIcon = new TrayIcon(image, "Spark", popup);
        trayIcon.setImageAutoSize(true);
        trayIcon.addActionListener(e -> Platform.runLater(showApplication));

        try {
            tray.add(trayIcon);
        } catch (AWTException e) {
            log.error("Failed to setup system tray", e);
        }
    }

    @Override
    public void close() {
        if (trayIcon != null && SystemTray.isSupported()) {
            SystemTray.getSystemTray().remove(trayIcon);
        }
    }
}