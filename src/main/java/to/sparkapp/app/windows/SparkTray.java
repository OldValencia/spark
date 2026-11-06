package to.sparkapp.app.windows;

import java.awt.*;
import java.io.IOException;

import javafx.application.Platform;

import javax.imageio.ImageIO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
final class SparkTray implements AutoCloseable {
    private final Runnable showApplication;
    private final Runnable exitApplication;
    private TrayIcon trayIcon;

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