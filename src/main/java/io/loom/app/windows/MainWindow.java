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

    private static final int WIDTH = 820;
    private static final int HEIGHT = 700;
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

        GlobalHotkeyManager globalHotkeyManager = null;
        try {
            globalHotkeyManager = new GlobalHotkeyManager(this, appPreferences);
            globalHotkeyManager.start();
            log.info("Global hotkey manager initialized successfully");
        } catch (Exception | UnsatisfiedLinkError e) {
            log.error("Failed to initialize global hotkey manager, hotkey feature will be disabled", e);
        }

        var settingsPanel = new SettingsPanel(appPreferences, globalHotkeyManager);
        settingsPanel.setOnRememberLastAiChanged(appPreferences::setRememberLastAi);
        settingsPanel.setOnClearCookies(cefWebView::clearCookies);
        settingsPanel.setOnZoomEnabledChanged(cefWebView::setZoomEnabled);
        settingsWindow = new SettingsWindow(frame, settingsPanel);
        cefWebView.setSettingsWindow(settingsWindow);

        var topBarArea = new TopBarArea(aiConfiguration, cefWebView, frame, settingsWindow, appPreferences, this::toggleSettings, this::closeWindow);
        root.add(topBarArea.createTopBar(), BorderLayout.NORTH);

        if (SystemTray.isSupported()) {
            setupTray();
        }

        frame.add(root);
        frame.setVisible(true);
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
        showItem.addActionListener(e -> {
            frame.setVisible(true);
            frame.setExtendedState(JFrame.NORMAL);
            frame.toFront();
            frame.requestFocus();
        });
        popup.add(showItem);

        popup.addSeparator();

        var exitItem = new MenuItem("Exit Loom");
        exitItem.addActionListener(e -> cefWebView.shutdown(() -> System.exit(0)));
        popup.add(exitItem);

        var trayIcon = new TrayIcon(image, "Loom", popup);
        trayIcon.setImageAutoSize(true);
        trayIcon.addActionListener(e -> {
            frame.setVisible(true);
            frame.setExtendedState(JFrame.NORMAL);
            frame.toFront();
            frame.requestFocus();
        });

        try {
            tray.add(trayIcon);
        } catch (AWTException e) {
            log.error("Failed to setup system tray", e);
        }
    }

    private void toggleSettings() {
        if (settingsWindow.isOpen()) {
            settingsWindow.close();
        } else {
            settingsWindow.open();
        }
    }

    private void closeWindow() {
        if (frame != null) {
            frame.setVisible(false);
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
        return frame;
    }
}
