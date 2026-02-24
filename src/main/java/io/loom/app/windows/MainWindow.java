package io.loom.app.windows;

import io.loom.app.config.AiConfiguration;
import io.loom.app.config.AppPreferences;
import io.loom.app.ui.FxWebViewPane;
import io.loom.app.ui.Theme;
import io.loom.app.ui.settings.SettingsPanel;
import io.loom.app.ui.topbar.TopBarArea;
import io.loom.app.ui.topbar.components.AiDock;
import io.loom.app.utils.GlobalHotkeyManager;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;

@Slf4j
public class MainWindow extends Stage {

    private final AiConfiguration aiConfiguration;
    private final AppPreferences appPreferences;

    private FxWebViewPane fxWebViewPane;
    private SettingsWindow settingsWindow;
    private BorderPane rootPane;
    private GlobalHotkeyManager globalHotkeyManager;
    private SplashScreen splashScreen;

    private boolean authMode = false;

    public static final int HEIGHT = 700;
    private static final int WIDTH = 820;
    private static final int RADIUS = 14;

    public MainWindow(AiConfiguration aiConfiguration, AppPreferences appPreferences) {
        this.aiConfiguration = aiConfiguration;
        this.appPreferences = appPreferences;

        this.setTitle("Loom");
        this.initStyle(StageStyle.TRANSPARENT);
        this.setAlwaysOnTop(true);
        this.setWidth(WIDTH);
        this.setHeight(HEIGHT);
        this.centerOnScreen();

        // Prevents JavaFX runtime from stopping when the main window is closed (keeps tray alive)
        Platform.setImplicitExit(false);

        this.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) {
                if (authMode && this.isAlwaysOnTop()) {
                    this.setAlwaysOnTop(false);
                }
            } else {
                if (!this.isAlwaysOnTop()) {
                    this.setAlwaysOnTop(true);
                }
            }
        });

        this.setOnCloseRequest(e -> {
            if (settingsWindow != null) {
                settingsWindow.close();
            }
        });

        this.iconifiedProperty().addListener((obs, oldVal, isIconified) -> {
            if (isIconified && settingsWindow != null) {
                settingsWindow.close();
            }
        });

        this.showingProperty().addListener((obs, oldVal, isShowing) -> {
            if (!isShowing && settingsWindow != null) {
                settingsWindow.close();
            }
        });

        this.xProperty().addListener((obs, oldVal, newVal) -> {
            if (settingsWindow != null) {
                settingsWindow.close();
            }
        });

        this.yProperty().addListener((obs, oldVal, newVal) -> {
            if (settingsWindow != null) {
                settingsWindow.close();
            }
        });
    }

    public void showWindow() {
        Platform.runLater(this::initializeOnFX);
    }

    public void setAuthMode(boolean isAuth) {
        this.authMode = isAuth;
        if (!isAuth && !this.isAlwaysOnTop()) {
            this.setAlwaysOnTop(true);
        }
    }

    private void handleProvidersChanged() {
        Platform.runLater(() -> {
            var activeIcons = aiConfiguration.getConfigurations().stream()
                    .map(AiConfiguration.AiConfig::icon)
                    .filter(icon -> icon != null && !icon.isEmpty())
                    .collect(Collectors.toList());
            AiDock.pruneIconCache(activeIcons);
            reloadTopBar();
        });
    }

    public void reloadTopBar() {
        Platform.runLater(() -> {
            aiConfiguration.reload();
            rootPane.setTop(null);

            var newTopBarArea = new TopBarArea(aiConfiguration, fxWebViewPane, this, settingsWindow, appPreferences,
                    this::toggleSettings, this::closeWindow);

            rootPane.setTop(newTopBarArea);
            log.info("TopBar reloaded with {} providers", aiConfiguration.getConfigurations().size());
        });
    }

    private void setupTray() {
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
        showItem.addActionListener(e -> Platform.runLater(this::showMainWindow));
        popup.add(showItem);

        popup.addSeparator();

        var exitItem = new MenuItem("Exit Loom");
        exitItem.addActionListener(e -> Platform.runLater(this::performShutdown));
        popup.add(exitItem);

        var trayIcon = new TrayIcon(image, "Loom", popup);
        trayIcon.setImageAutoSize(true);
        trayIcon.addActionListener(e -> Platform.runLater(this::showMainWindow));

        try {
            tray.add(trayIcon);
        } catch (AWTException e) {
            log.error("Failed to setup system tray", e);
        }
    }

    private void showMainWindow() {
        this.show();
        this.setIconified(false);
        this.toFront();
        this.requestFocus();
    }

    private void toggleSettings() {
        if (settingsWindow.isOpen() && !settingsWindow.isShowing()) {
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
        this.hide();
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

        this.hide();
        AiDock.clearIconCache();

        if (fxWebViewPane != null) {
            fxWebViewPane.shutdown(() -> log.info("Webview shutdown complete"));
        } else {
            System.exit(0);
        }
    }

    private void initializeOnFX() {
        try {
            splashScreen = new SplashScreen();
            splashScreen.showSplash();

            rootPane = createRootPane();
            Scene scene = new Scene(rootPane, WIDTH, HEIGHT, Color.TRANSPARENT);
            this.setScene(scene);

            if (splashScreen != null) {
                splashScreen.updateStatus("Initializing browser engine...");
            }

            fxWebViewPane = getFxWebViewPane();
            rootPane.setCenter(fxWebViewPane);

            try {
                globalHotkeyManager = new GlobalHotkeyManager(this, settingsWindow, appPreferences);
                globalHotkeyManager.start();
                log.info("Global hotkey manager initialized");
            } catch (Exception | UnsatisfiedLinkError e) {
                log.warn("Failed to initialize global hotkey manager", e);
            }

            var settingsPanel = new SettingsPanel(appPreferences, globalHotkeyManager, aiConfiguration);
            settingsPanel.setOnRememberLastAiChanged(appPreferences::setRememberLastAi);
            settingsPanel.setOnClearCookies(fxWebViewPane::clearCookies);
            settingsPanel.setOnProvidersChanged(this::handleProvidersChanged);

            settingsWindow = new SettingsWindow(this, settingsPanel);

            var topBarArea = new TopBarArea(aiConfiguration, fxWebViewPane, this, settingsWindow, appPreferences,
                    this::toggleSettings, this::closeWindow);
            rootPane.setTop(topBarArea);

            setupTray();

            var showTimer = new Timer();
            showTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    Platform.runLater(() -> {
                        if (splashScreen != null) {
                            splashScreen.hideSplash();
                        }
                        if (!appPreferences.isStartApplicationHiddenEnabled()) {
                            show();
                        }
                    });
                }
            }, 500);

        } catch (Exception e) {
            log.error("Failed to initialize application", e);
            if (splashScreen != null) {
                splashScreen.hideSplash();
            }
            System.exit(1);
        }
    }

    private BorderPane createRootPane() {
        var pane = new BorderPane();
        pane.setStyle(
                "-fx-background-color: " + Theme.toHex(Theme.BG_DEEP) + ";" +
                        "-fx-background-radius: " + RADIUS + "px;" +
                        "-fx-border-radius: " + RADIUS + "px;"
        );
        return pane;
    }

    private FxWebViewPane getFxWebViewPane() {
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

        var pane = new FxWebViewPane(startUrl, appPreferences, this::toggleSettings);
        pane.setOnAuthPageDetected(this::setAuthMode);

        return pane;
    }
}
