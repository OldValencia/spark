package to.sparkapp.app.windows;

import java.awt.*;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import lombok.extern.slf4j.Slf4j;
import to.sparkapp.app.config.AiConfiguration;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.ui.Theme;
import to.sparkapp.app.ui.settings.SettingsPanel;
import to.sparkapp.app.ui.topbar.TopBarArea;
import to.sparkapp.app.ui.topbar.SitesEditor;
import to.sparkapp.app.ui.topbar.components.AiDock;
import to.sparkapp.app.ui.webview.FxWebViewPane;
import to.sparkapp.app.utils.GlobalHotkeyManager;
import to.sparkapp.app.utils.NativeWindowUtils;

@Slf4j
public class MainWindow extends Stage {

    private final AiConfiguration aiConfiguration;
    private final AppPreferences appPreferences;

    private FxWebViewPane fxWebViewPane;
    private SettingsWindow settingsWindow;
    private BorderPane rootPane;
    private GlobalHotkeyManager globalHotkeyManager;
    private WindowController windowController;
    private TopBarArea topBar;
    private long previousForeground;

    // Сохраняем иконку трея, чтобы можно было удалить её при выходе
    private final SparkTray tray = new SparkTray(this::showMainWindow, this::performShutdown);
    private WindowAutoHide autoHide;

    private boolean authMode = false;

    public static final int HEIGHT = 700;
    private static final int WIDTH = 860;
    private static final int RADIUS = 14;

    public MainWindow(AiConfiguration aiConfiguration, AppPreferences appPreferences) {
        this.aiConfiguration = aiConfiguration;
        this.appPreferences = appPreferences;

        this.setTitle("Spark");
        this.initStyle(StageStyle.TRANSPARENT);
        this.setAlwaysOnTop(appPreferences.isAlwaysOnTop());
        this.setWidth(WIDTH);
        this.setHeight(HEIGHT);
        this.centerOnScreen();

        Platform.setImplicitExit(false);

        this.focusedProperty().addListener((obs, oldVal, newVal) -> {
            this.setAlwaysOnTop(appPreferences.isAlwaysOnTop() && (newVal || !authMode));

            if (!newVal && windowController != null) {
                windowController.cancelInteraction();
            }

            if (!newVal && topBar != null) {
                topBar.getDock().cancelInteraction();
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
        this.setAlwaysOnTop(appPreferences.isAlwaysOnTop() && (!isAuth || isFocused()));
    }

    /**
     * Shows the main window and wakes up the webview. Public so that both the tray action and
     * GlobalHotkeyManager can use it.
     */
    public void showMainWindow() {
        showMainWindow(System.nanoTime());
    }

    public void showMainWindow(long requestedAt) {
        if (!isShowing() && !NativeWindowUtils.isSparkForeground()) {
            previousForeground = NativeWindowUtils.foregroundWindow();
        }

        if (autoHide != null) {
            autoHide.shown();
        }

        if (windowController != null) {
            windowController.opening();
        }

        setAlwaysOnTop(appPreferences.isAlwaysOnTop());
        this.show();
        this.setIconified(false);
        this.toFront();
        this.requestFocus();

        if (fxWebViewPane != null) {
            fxWebViewPane.onWindowRestored(requestedAt);
        }
    }

    /**
     * Hides the main window and hibernates the webview. Public so that GlobalHotkeyManager can use
     * it alongside showMainWindow().
     */
    public void hideMainWindow() {
        hideMainWindow(true);
    }

    private void hideMainWindow(boolean restoreFocus) {
        if (autoHide != null) {
            autoHide.cancel();
        }

        if (windowController != null) {
            windowController.closing();
        }

        if (topBar != null) {
            topBar.closeMenus();
        }

        if (settingsWindow != null && settingsWindow.isOpen()) {
            settingsWindow.close();
        }

        if (fxWebViewPane != null) {
            fxWebViewPane.onWindowHidden();
        }

        this.hide();

        if (restoreFocus) {
            NativeWindowUtils.restoreForeground(previousForeground);
        }
    }

    public void onGlobalMousePressed() {
        if (autoHide != null) {
            autoHide.onGlobalMousePressed();
        }
    }

    public void onGlobalMouseMoved(double x, double y, long recordedAtNanos) {
        if (autoHide != null) {
            autoHide.onGlobalMouseMoved(x, y, recordedAtNanos);
        }
    }

    private void handleProvidersChanged() {
        Platform.runLater(() -> {
            AiDock.clearIconCache();
            reloadTopBar();
        });
    }

    public void reloadTopBar() {
        Platform.runLater(() -> {
            aiConfiguration.reload();
            fxWebViewPane.reconcileSites(aiConfiguration.getConfigurations());

            if (topBar != null) {
                topBar.dispose();
            }

            rootPane.setTop(null);
            installTopBar();
            log.info("TopBar reloaded with {} providers", aiConfiguration.getConfigurations().size());
        });
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

    private void performShutdown() {
        log.info("Starting application shutdown...");

        var shutdownWatchdog = new Thread(() -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException ignored) {
            }

            log.warn("Shutdown timeout reached. Forcing exit.");
            System.exit(0);
        }, "spark-shutdown-watchdog");
        // The fallback must not keep a cleanly stopped application alive.
        shutdownWatchdog.setDaemon(true);
        shutdownWatchdog.start();

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

        if (windowController != null) {
            windowController.dispose();
        }

        if (autoHide != null) {
            autoHide.cancel();
        }

        if (topBar != null) {
            topBar.dispose();
        }

        this.hide();
        AiDock.clearIconCache();
        appPreferences.flush();

        tray.close();

        if (fxWebViewPane != null) {
            try {
                fxWebViewPane.shutdown(() -> {
                    log.info("Webview shutdown complete");
                    Platform.exit();
                });
            } catch (Exception e) {
                log.error("Error initiating Webview shutdown", e);
                System.exit(0);
            }
        } else {
            Platform.exit();
        }
    }

    private void initializeOnFX() {
        try {
            rootPane = createRootPane();

            Scene scene = new Scene(rootPane, WIDTH, HEIGHT, Color.TRANSPARENT);
            this.setScene(scene);
            scene.getStylesheets().add(getClass().getResource("/css/shell.css").toExternalForm());
            windowController = new WindowController(this, appPreferences);
            windowController.setOnReveal(this::showMainWindow);
            autoHide = new WindowAutoHide(this, appPreferences, windowController, () -> authMode,
                    () -> hideMainWindow(false));
            windowController.dockedProperty().addListener((o, before, docked) -> updateShellStyle());

            var grip = new Region();
            grip.setPrefSize(36, 3);
            grip.setMaxSize(36, 3);
            grip.setStyle("-fx-background-color:#90909c77;-fx-background-radius:3;");

            var foot = new StackPane(grip);
            foot.setMinHeight(12);
            foot.setPrefHeight(12);
            foot.setMaxHeight(12);
            rootPane.setBottom(foot);
            windowController.installResize(scene, foot);
            updateShellStyle();

            fxWebViewPane = getFxWebViewPane();
            rootPane.setCenter(fxWebViewPane);

            if (appPreferences.isStartApplicationHiddenEnabled()) {
                fxWebViewPane.onWindowHidden();
            }

            try {
                globalHotkeyManager = new GlobalHotkeyManager(this, appPreferences);
                globalHotkeyManager.start();
                log.info("Global hotkey manager initialized");
            } catch (Exception | UnsatisfiedLinkError e) {
                log.warn("Failed to initialize global hotkey manager", e);
            }

            var settingsPanel = new SettingsPanel(appPreferences, globalHotkeyManager);
            settingsPanel.setOnRememberLastAiChanged(appPreferences::setRememberLastAi);
            settingsPanel.setOnClearCookies(fxWebViewPane::clearCookies);
            settingsPanel.setOnBackgroundMemoryChanged(fxWebViewPane::refreshResourcePolicy);
            settingsPanel.setOnPillSettingsChanged(windowController::refreshPill);
            settingsPanel.setOnZoomEnabledChanged(enabled -> fxWebViewPane.resetZoom());

            settingsWindow = new SettingsWindow(this, settingsPanel);

            installTopBar();

            tray.show();

            if (!appPreferences.isStartApplicationHiddenEnabled()) {
                showMainWindow();
            } else {
                windowController.refreshPill();
            }

        } catch (Exception e) {
            log.error("Failed to initialize application", e);
            System.exit(1);
        }
    }

    private BorderPane createRootPane() {
        var pane = new BorderPane();
        pane.setPadding(new Insets(0, 5, 0, 5));
        pane.setStyle("-fx-background-color: " + Theme.toHex(Theme.BG_DEEP) + ";" + "-fx-background-radius: " + RADIUS +
                "px;" + "-fx-border-radius: " + RADIUS + "px;");

        return pane;
    }

    private void installTopBar() {
        topBar = new TopBarArea(aiConfiguration, fxWebViewPane, this, appPreferences, this::toggleSettings,
                this::hideMainWindow, windowController, this::handleProvidersChanged);
        topBar.getDock().setOnAccentChanged(accent -> {
            updateShellStyle();
        });
        rootPane.setTop(topBar);
    }

    private void updateShellStyle() {
        if (rootPane == null) {
            return;
        }

        var radius = windowController != null && windowController.isDocked() ? "0 0 16 16" : "16";
        var outline = "#414247";
        rootPane.setStyle(
                "-fx-background-color:#25262a;-fx-background-radius:" + radius + ";-fx-border-radius:" + radius +
                        ";-fx-border-color:" + outline + ";-fx-border-width:1;");
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

        var pane = new FxWebViewPane(startUrl, appPreferences);
        pane.setOnAuthPageDetected(this::setAuthMode);
        var editor = new SitesEditor(this, aiConfiguration, this::handleProvidersChanged);
        pane.setOnEditSite(editor::edit);

        return pane;
    }
}
