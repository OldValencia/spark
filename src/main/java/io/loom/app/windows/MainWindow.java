package io.loom.app.windows;

import io.loom.app.config.AiConfiguration;
import io.loom.app.config.AppPreferences;
import io.loom.app.ui.CefWebView;
import io.loom.app.ui.Theme;
import io.loom.app.ui.settings.SettingsPanel;
import io.loom.app.ui.topbar.TopBarArea;
import io.loom.app.utils.GlobalHotkeyManager;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.RoundRectangle2D;

@Slf4j
@RequiredArgsConstructor
public class MainWindow {

    private final AiConfiguration aiConfiguration;
    private final AppPreferences appPreferences;

    private CefWebView cefWebView;
    private SettingsWindow settingsWindow;
    @Getter
    private JFrame frame;
    private GlobalHotkeyManager globalHotkeyManager;

    public static final int HEIGHT = 700;
    private static final int WIDTH = 820;
    private static final int RADIUS = 14;

    public void showWindow() {
        var root = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                var g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.BG_DEEP);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), RADIUS, RADIUS));
            }
        };
        root.setBackground(Theme.BG_DEEP);

        cefWebView = getCefWebView();
        root.add(cefWebView, BorderLayout.CENTER);

        frame = buildMainFrame();

        try {
            globalHotkeyManager = new GlobalHotkeyManager(this, appPreferences);
            globalHotkeyManager.start();
            log.info("Global hotkey manager initialized successfully");
        } catch (Exception | UnsatisfiedLinkError e) {
            log.error("Failed to initialize global hotkey manager, hotkey feature will be disabled", e);
        }

        var settingsPanel = new SettingsPanel(appPreferences, globalHotkeyManager, aiConfiguration);
        settingsPanel.setOnRememberLastAiChanged(appPreferences::setRememberLastAi);
        settingsPanel.setOnClearCookies(cefWebView::clearCookies);
        settingsPanel.setOnZoomEnabledChanged(cefWebView::setZoomEnabled);
        settingsPanel.setOnProvidersChanged(this::handleProvidersChanged);
        settingsWindow = new SettingsWindow(frame, settingsPanel);
        cefWebView.setSettingsWindow(settingsWindow);

        var topBarArea = new TopBarArea(aiConfiguration, cefWebView, frame, settingsWindow, appPreferences, this::toggleSettings, this::closeWindow);
        root.add(topBarArea.createTopBar(), BorderLayout.NORTH);

        if (SystemTray.isSupported()) {
            setupTray();
        }

        frame.add(root);
        frame.setVisible(!appPreferences.isStartApplicationHiddenEnabled());
    }

    private void handleProvidersChanged() {
        // Перезагружаем TopBarArea с новыми провайдерами
        SwingUtilities.invokeLater(() -> {
            // TODO: обновить AiDock
        });
    }

    private void setupTray() {
        var tray = SystemTray.getSystemTray();
        var iconUrl = getClass().getResource("/app-icons/icon.png");
        if (iconUrl == null) {
            iconUrl = getClass().getResource("/app-icons/icon.ico");
        }
        var image = Toolkit.getDefaultToolkit().getImage(iconUrl);
        var popup = new PopupMenu();

        var showItem = new MenuItem("Show Application");
        showItem.addActionListener(e -> showMainWindow());
        popup.add(showItem);

        popup.addSeparator();

        var exitItem = new MenuItem("Exit Loom");
        exitItem.addActionListener(e -> performShutdown());
        popup.add(exitItem);

        var trayIcon = new TrayIcon(image, "Loom", popup);
        trayIcon.setImageAutoSize(true);
        trayIcon.addActionListener(e -> showMainWindow());

        try {
            tray.add(trayIcon);
        } catch (AWTException e) {
            log.error("Failed to setup system tray", e);
        }
    }

    private void showMainWindow() {
        frame.setVisible(true);
        frame.setExtendedState(JFrame.NORMAL);
        frame.toFront();
        frame.requestFocus();
    }

    private void toggleSettings() {
        if (!frame.isVisible() && settingsWindow.isOpen()) {
            settingsWindow.close();
            return;
        }

        if (settingsWindow.isOpen()) {
            settingsWindow.close();
        } else {
            settingsWindow.open();
        }
    }

    private void closeWindow() {
        if (settingsWindow != null && settingsWindow.isOpen()) {
            settingsWindow.close();
        }

        if (frame != null) {
            frame.setVisible(false);
        }
    }

    private void performShutdown() {
        log.info("Starting application shutdown...");

        if (globalHotkeyManager != null) {
            try {
                globalHotkeyManager.stop();
                log.info("Global hotkey manager stopped");
            } catch (Exception e) {
                log.error("Error stopping hotkey manager", e);
            }
        }

        if (settingsWindow != null && settingsWindow.isOpen()) {
            settingsWindow.close();
        }

        if (frame != null) {
            frame.setVisible(false);
        }

        if (cefWebView != null) {
            cefWebView.shutdown(() -> {
                log.info("CEF shutdown complete, exiting...");
                System.exit(0);
            });
        } else {
            System.exit(0);
        }
    }

    private CefWebView getCefWebView() {
        String startUrl = null;

        if (appPreferences.isRememberLastAi()) {
            startUrl = appPreferences.getLastUrl();
        }

        if (startUrl == null && !aiConfiguration.getConfigurations().isEmpty()) {
            startUrl = aiConfiguration.getConfigurations().getFirst().url();
        }

        if (startUrl == null) {
            startUrl = "https://chatgpt.com";
        }

        return new CefWebView(startUrl, appPreferences, this::toggleSettings);
    }

    private JFrame buildMainFrame() {
        var frame = new JFrame("Loom");
        frame.setUndecorated(true);
        frame.setAlwaysOnTop(true);
        frame.setSize(WIDTH, HEIGHT);
        frame.setLocationRelativeTo(null);
        frame.setBackground(new Color(0, 0, 0, 0));
        frame.setShape(new RoundRectangle2D.Double(0, 0, WIDTH, HEIGHT, RADIUS, RADIUS));
        frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (settingsWindow != null && settingsWindow.isOpen()) {
                    settingsWindow.close();
                }
            }
        });

        return frame;
    }
}