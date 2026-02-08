package io.loom.app.windows;

import io.loom.app.config.AiConfiguration;
import io.loom.app.config.AppPreferences;
import io.loom.app.ui.CefWebView;
import io.loom.app.ui.Theme;
import io.loom.app.ui.settings.SettingsPanel;
import io.loom.app.ui.topbar.TopBarArea;
import io.loom.app.ui.topbar.components.AiDock;
import io.loom.app.utils.GlobalHotkeyManager;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
public class MainWindow extends JFrame {

    private final AiConfiguration aiConfiguration;
    private final AppPreferences appPreferences;

    private CefWebView cefWebView;
    private SettingsWindow settingsWindow;
    private JPanel rootPanel;
    private GlobalHotkeyManager globalHotkeyManager;
    private SplashScreen splashScreen;

    private boolean authMode = false;

    public static final int HEIGHT = 700;
    private static final int WIDTH = 820;
    private static final int RADIUS = 14;

    public MainWindow(AiConfiguration aiConfiguration, AppPreferences appPreferences) {
        super("Loom");
        this.aiConfiguration = aiConfiguration;
        this.appPreferences = appPreferences;

        this.setUndecorated(true);
        this.setAlwaysOnTop(true);
        this.setSize(WIDTH, HEIGHT);
        this.setLocationRelativeTo(null);
        this.setBackground(new Color(0, 0, 0, 0));
        this.setShape(new RoundRectangle2D.Double(0, 0, WIDTH, HEIGHT, RADIUS, RADIUS));
        this.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

        this.addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowLostFocus(WindowEvent e) {
                if (authMode && isAlwaysOnTop()) {
                    setAlwaysOnTop(false);
                }
            }

            @Override
            public void windowGainedFocus(WindowEvent e) {
                if (!isAlwaysOnTop()) {
                    setAlwaysOnTop(true);
                }
            }
        });

        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                settingsWindow.close();
            }

            @Override
            public void windowIconified(WindowEvent e) {
                settingsWindow.close();
            }

            @Override
            public void windowDeiconified(WindowEvent e) {
                // do nothing here
            }
        });

        this.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentHidden(ComponentEvent e) {
                settingsWindow.close();
            }
        });

        this.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent e) {
                settingsWindow.close();
            }
        });
    }

    public void showWindow() {
        SwingUtilities.invokeLater(this::initializeOnEDT);
    }

    public void setAuthMode(boolean isAuth) {
        this.authMode = isAuth;
        if (!isAuth && !this.isAlwaysOnTop()) {
            this.setAlwaysOnTop(true);
        }
    }

    private void handleProvidersChanged() {
        SwingUtilities.invokeLater(() -> {
            var activeIcons = aiConfiguration.getConfigurations().stream()
                    .map(AiConfiguration.AiConfig::icon)
                    .filter(icon -> icon != null && !icon.isEmpty())
                    .collect(Collectors.toList());
            AiDock.pruneIconCache(activeIcons);
            reloadTopBar();
        });
    }

    public void reloadTopBar() {
        SwingUtilities.invokeLater(() -> {
            aiConfiguration.reload();

            Component topBarToRemove = null;
            for (Component comp : rootPanel.getComponents()) {
                if (comp.getClass().getSimpleName().contains("Panel")) {
                    var constraints = ((BorderLayout) rootPanel.getLayout()).getConstraints(comp);
                    if (constraints != null && constraints.equals(BorderLayout.NORTH)) {
                        topBarToRemove = comp;
                        break;
                    }
                }
            }

            if (topBarToRemove != null) {
                rootPanel.remove(topBarToRemove);
            }

            var newTopBarArea = new TopBarArea(aiConfiguration, cefWebView, this, settingsWindow, appPreferences,
                    this::toggleSettings, this::closeWindow);

            rootPanel.add(newTopBarArea, BorderLayout.NORTH);
            rootPanel.revalidate();
            rootPanel.repaint();

            log.info("TopBar reloaded with {} providers", aiConfiguration.getConfigurations().size());
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
        this.setVisible(true);
        this.setExtendedState(JFrame.NORMAL);
        this.toFront();
        this.requestFocus();
    }

    private void toggleSettings() {
        if (settingsWindow.isOpen() && !settingsWindow.isVisible()) {
            settingsWindow.open();
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
        this.setVisible(false);
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

        this.setVisible(false);
        AiDock.clearIconCache();

        if (cefWebView != null) {
            cefWebView.shutdown(() -> {
                log.info("CEF shutdown complete, exiting...");
                System.exit(0);
            });
        } else {
            System.exit(0);
        }
    }

    private void initializeOnEDT() {
        try {
            splashScreen = new SplashScreen();
            splashScreen.showSplash();

            rootPanel = createRootPanel();

            if (splashScreen != null) {
                splashScreen.updateStatus("Initializing browser engine...");
            }

            cefWebView = getCefWebView();
            rootPanel.add(cefWebView, BorderLayout.CENTER);

            try {
                globalHotkeyManager = new GlobalHotkeyManager(this, settingsWindow, appPreferences);
                globalHotkeyManager.start();
                log.info("Global hotkey manager initialized");
            } catch (Exception | UnsatisfiedLinkError e) {
                log.warn("Failed to initialize global hotkey manager", e);
            }

            var settingsPanel = new SettingsPanel(appPreferences, globalHotkeyManager, aiConfiguration);
            settingsPanel.setOnRememberLastAiChanged(appPreferences::setRememberLastAi);
            settingsPanel.setOnClearCookies(cefWebView::clearCookies);
            settingsPanel.setOnZoomEnabledChanged(cefWebView::setZoomEnabled);
            settingsPanel.setOnProvidersChanged(this::handleProvidersChanged);
            settingsWindow = new SettingsWindow(this, settingsPanel);
            cefWebView.setSettingsWindow(settingsWindow);

            var topBarArea = new TopBarArea(aiConfiguration, cefWebView, this, settingsWindow, appPreferences,
                    this::toggleSettings, this::closeWindow);
            rootPanel.add(topBarArea, BorderLayout.NORTH);

            if (SystemTray.isSupported()) {
                setupTray();
            }

            this.add(rootPanel);

            var showTimer = new Timer(500, e -> {
                ((Timer) e.getSource()).stop();
                if (splashScreen != null) {
                    splashScreen.hideSplash();
                }
                this.setVisible(!appPreferences.isStartApplicationHiddenEnabled());
            });
            showTimer.start();

        } catch (Exception e) {
            log.error("Failed to initialize application", e);
            if (splashScreen != null) {
                splashScreen.hideSplash();
            }
            JOptionPane.showMessageDialog(null,
                    "Failed to initialize: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    private JPanel createRootPanel() {
        JPanel panel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                var g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.BG_DEEP);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), RADIUS, RADIUS));
            }
        };
        panel.setBackground(Theme.BG_DEEP);
        return panel;
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

        Consumer<String> onStatusUpdate = status -> {
            if (splashScreen != null) {
                SwingUtilities.invokeLater(() -> splashScreen.updateStatus(status));
            }
        };
        return new CefWebView(startUrl, appPreferences, this::toggleSettings, onStatusUpdate);
    }
}
