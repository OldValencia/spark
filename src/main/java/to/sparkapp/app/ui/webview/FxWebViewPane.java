package to.sparkapp.app.ui.webview;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import to.sparkapp.app.browser.MacOsWebviewBridge;
import to.sparkapp.app.browser.WebviewManager;
import to.sparkapp.app.browser.WebviewNavigator;
import to.sparkapp.app.config.AiConfiguration;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.ui.Theme;
import to.sparkapp.app.ui.topbar.components.AiDock;
import to.sparkapp.app.utils.NativeWindowUtils;
import to.sparkapp.app.utils.SystemUtils;

import java.util.function.Consumer;

@Slf4j
public class FxWebViewPane extends StackPane {

    private WebviewManager bridge;
    private MacOsWebviewBridge macBridge;

    private final AppPreferences appPreferences;
    private final String startUrl;
    private final WebViewLoadingOverlay overlay;

    private boolean bridgeStarted = false;

    @Setter
    private Consumer<Double> zoomCallback;
    @Setter
    private Consumer<Boolean> onAuthPageDetected;

    public FxWebViewPane(String startUrl, AppPreferences appPreferences) {
        this.startUrl = startUrl;
        this.appPreferences = appPreferences;

        setPadding(Insets.EMPTY);
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        setStyle("-fx-background-color: " + Theme.toHex(Theme.BG_DEEP) + ";");

        overlay = new WebViewLoadingOverlay();
        getChildren().add(overlay);

        if (SystemUtils.isMac()) {
            initMacBridge();
        } else {
            initWindowsBridge();
        }
    }

    private void initMacBridge() {
        macBridge = new MacOsWebviewBridge(appPreferences);

        macBridge.setOnReadyCallback(() -> Platform.runLater(() -> {
            if (overlay.isActive()) overlay.deactivate();
        }));

        macBridge.setZoomCallback(pct -> {
            if (zoomCallback != null) zoomCallback.accept(pct);
        });

        macBridge.setOnUrlChangedCallback(url -> {
            if (appPreferences.isRememberLastAi()) {
                appPreferences.setLastUrl(url);
            }
        });

        macBridge.setOnAuthPageDetected(isAuth -> {
            if (onAuthPageDetected != null) onAuthPageDetected.accept(isAuth);
        });

        macBridge.registerZoomBridge();

        var webViewNode = macBridge.getWebView();
        webViewNode.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        getChildren().add(0, webViewNode);

        bridgeStarted = true;

        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) return;
            newScene.windowProperty().addListener((wObs, oldWin, newWin) -> {
                if (newWin == null) return;
                newWin.showingProperty().addListener((o, old, isShowing) -> {
                    if (isShowing) {
                        macBridge.init(startUrl);
                    }
                });
            });
        });
    }

    private void initWindowsBridge() {
        bridge = new WebviewManager(appPreferences);
        setupWindowsBridgeCallbacks();
        setupLayoutListeners();
    }

    private void setupWindowsBridgeCallbacks() {
        bridge.setOnReadyCallback(() -> Platform.runLater(() -> {
            syncBounds();
            bridge.setVisible(true);
            if (overlay.isActive()) {
                overlay.deactivate();
            }
        }));

        bridge.setZoomCallback(pct -> {
            if (zoomCallback != null) zoomCallback.accept(pct);
        });

        bridge.setOnUrlChanged(url -> {
            checkIfAuthPage(url);
            if (appPreferences.isRememberLastAi()) {
                appPreferences.setLastUrl(url);
            }
        });
    }

    private void setupLayoutListeners() {
        boundsInLocalProperty().addListener((obs, o, n) -> {
            if (!bridgeStarted) startBridgeIfReady();
            else syncBounds();
        });

        localToSceneTransformProperty().addListener((obs, o, n) -> syncBounds());

        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) return;
            newScene.windowProperty().addListener((wObs, oldWin, newWin) -> {
                if (newWin == null) return;
                newWin.showingProperty().addListener((o, old, isShowing) -> {
                    if (isShowing) {
                        if (!bridgeStarted) startBridgeIfReady();
                    } else if (bridgeStarted) {
                        bridge.hibernate();
                    }
                });
            });
        });
    }

    public void onWindowRestored() {
        if (SystemUtils.isMac()) {
            return;
        }
        var delay = new PauseTransition(Duration.millis(100));
        delay.setOnFinished(e -> doWakeupBridge());
        delay.play();
    }

    public void onWindowHidden() {
        if (SystemUtils.isMac()) {
            return;
        }
        bridge.hibernate();
    }

    private synchronized void startBridgeIfReady() {
        if (bridgeStarted || bridge == null) {
            return;
        }
        var scene = getScene();
        if (scene == null) return;
        var window = scene.getWindow();
        if (window == null || !window.isShowing()) return;

        long parentHandle = resolveParentHandle(window);
        if (parentHandle == 0L && SystemUtils.isWindows()) {
            retryStartBridge();
            return;
        }

        bridgeStarted = true;
        bridge.init(startUrl, parentHandle, 0, 0, 10, 10);
        Platform.runLater(this::syncBounds);
    }

    private long resolveParentHandle(javafx.stage.Window window) {
        if (!SystemUtils.isWindows() || !(window instanceof Stage stage)) {
            return 0L;
        }
        var title = stage.getTitle();
        boolean tempTitle = title == null || title.isEmpty();
        if (tempTitle) {
            title = "SparkMainWindow-" + System.nanoTime();
            stage.setTitle(title);
        }
        long handle = NativeWindowUtils.getJavaFXWindowHandle(title);
        if (tempTitle) {
            stage.setTitle("");
        }
        return handle;
    }

    private void retryStartBridge() {
        var t = new PauseTransition(Duration.millis(50));
        t.setOnFinished(e -> startBridgeIfReady());
        t.play();
    }

    private void doWakeupBridge() {
        var window = getScene() != null ? getScene().getWindow() : null;
        if (window == null || !window.isShowing()) {
            return;
        }
        long parentHandle = resolveParentHandle(window);
        if (parentHandle == 0L && SystemUtils.isWindows()) {
            var t = new PauseTransition(Duration.millis(50));
            t.setOnFinished(e -> doWakeupBridge());
            t.play();
            return;
        }

        log.info("FxWebViewPane: Waking up bridge with parentHandle=0x{}", Long.toHexString(parentHandle));
        bridge.wakeup(parentHandle);

        var root = getScene().getRoot();
        root.applyCss();
        root.layout();
        syncBounds();
    }

    void syncBounds() {
        if (bridge == null || !bridgeStarted || getScene() == null || getScene().getWindow() == null) {
            return;
        }
        var window = getScene().getWindow();
        var scene = getScene();
        var bounds = localToScene(getBoundsInLocal());
        if (bounds == null) return;

        int x, y, w, h;
        if (SystemUtils.isWindows()) {
            double sx = window.getOutputScaleX();
            double sy = window.getOutputScaleY();
            x = (int) Math.round(bounds.getMinX() * sx);
            y = (int) Math.round(bounds.getMinY() * sy);
            w = (int) Math.round(bounds.getWidth() * sx);
            h = (int) Math.round(bounds.getHeight() * sy);
        } else {
            x = (int) Math.round(bounds.getMinX() + scene.getX());
            y = (int) Math.round(bounds.getMinY() + scene.getY());
            w = (int) Math.round(bounds.getWidth());
            h = (int) Math.round(bounds.getHeight());
        }

        bridge.updateBounds(x, y, w, h);
    }

    public void setCurrentConfig(AiConfiguration.AiConfig config) {
        var icon = AiDock.ICON_CACHE.get(config.icon());

        if (SystemUtils.isMac()) {
            overlay.activate(icon, 1000, null);
            macBridge.setCurrentConfig(config);
            return;
        }

        if (!bridgeStarted) {
            overlay.activate(icon, 0, null);
            bridge.setCurrentConfig(config);
            return;
        }

        bridge.setVisible(false);
        overlay.activate(icon, 1200, () -> {
            syncBounds();
            bridge.setVisible(true);
        });
        bridge.setCurrentConfig(config);
    }

    public void clearCookies() {
        if (SystemUtils.isMac()) {
            macBridge.clearCookies();
        } else {
            bridge.clearCookies();
        }
    }

    public void resetZoom() {
        if (SystemUtils.isMac()) {
            macBridge.resetZoom();
        } else {
            bridge.resetZoom();
        }
    }

    public void restart() {
        var url = appPreferences.getLastUrl() != null ? appPreferences.getLastUrl() : startUrl;
        if (SystemUtils.isMac()) {
            macBridge.navigate(url);
        } else {
            bridge.navigate(url);
        }
    }

    public void shutdown(Runnable onComplete) {
        if (SystemUtils.isMac()) {
            macBridge.shutdown(() -> Platform.runLater(() -> {
                onComplete.run();
                System.exit(0);
            }));
        } else {
            bridge.shutdown(() -> Platform.runLater(() -> {
                onComplete.run();
                System.exit(0);
            }));
        }
    }

    private void checkIfAuthPage(String url) {
        var isAuth = WebviewNavigator.isAuthUrl(url);

        if (isAuth == null) {
            return;
        }

        if (onAuthPageDetected != null) {
            Platform.runLater(() -> onAuthPageDetected.accept(isAuth));
        }
    }
}
