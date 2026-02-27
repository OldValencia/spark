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
import to.sparkapp.app.browser.WebviewManager;
import to.sparkapp.app.browser.WebviewNavigator;
import to.sparkapp.app.config.AiConfiguration;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.ui.Theme;
import to.sparkapp.app.ui.topbar.components.AiDock;
import to.sparkapp.app.utils.NativeWindowUtils;
import to.sparkapp.app.utils.SystemUtils;

import java.util.function.Consumer;

/**
 * The main JavaFX pane that hosts the native WebView browser.
 *
 * <p>The native webview is a separate OS window that is parented and positioned
 * to perfectly overlap this pane. A {@link WebViewLoadingOverlay} is shown
 * above while navigation is in progress.
 */
@Slf4j
public class FxWebViewPane extends StackPane {

    private final WebviewManager bridge;
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
        this.bridge = new WebviewManager(appPreferences);

        setPadding(Insets.EMPTY);
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        setStyle("-fx-background-color: " + Theme.toHex(Theme.BG_DEEP) + ";");

        overlay = new WebViewLoadingOverlay();
        getChildren().add(overlay);

        setupBridgeCallbacks();
        setupLayoutListeners();
    }

    private void setupBridgeCallbacks() {
        bridge.setOnReadyCallback(() -> Platform.runLater(() -> {
            syncBounds();
            bridge.setVisible(true);
            if (overlay.isActive()) {
                overlay.deactivate();
            }
        }));

        bridge.setZoomCallback(pct -> {
            if (zoomCallback != null) {
                zoomCallback.accept(pct);
            }
        });

        bridge.setOnUrlChanged(url -> {
            if (onAuthPageDetected != null) {
                Platform.runLater(() -> onAuthPageDetected.accept(WebviewNavigator.isAuthUrl(url)));
            }
            if (appPreferences.isRememberLastAi()) {
                appPreferences.setLastUrl(url);
            }
        });
    }

    private void setupLayoutListeners() {
        boundsInLocalProperty().addListener((obs, o, n) -> {
            if (!bridgeStarted) {
                startBridgeIfReady();
            } else {
                syncBounds();
            }
        });

        localToSceneTransformProperty().addListener((obs, o, n) -> syncBounds());

        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                return;
            }
            newScene.windowProperty().addListener((wObs, oldWin, newWin) -> {
                if (newWin == null) {
                    return;
                }

                newWin.showingProperty().addListener((o, old, isShowing) -> {
                    if (isShowing) {
                        if (!bridgeStarted) {
                            startBridgeIfReady();
                        }
                    } else if (bridgeStarted) {
                        bridge.hibernate();
                    }
                });

                // macOS: the webview is a floating NSWindow — sync position whenever
                // the JavaFX window moves, because there is no parent-child relationship.
                if (SystemUtils.isMac()) {
                    newWin.xProperty().addListener((o, ov, nv) -> syncBounds());
                    newWin.yProperty().addListener((o, ov, nv) -> syncBounds());
                }
            });
        });
    }

    /**
     * Call after the host window becomes visible (tray restore or hotkey show).
     * Deferred 100 ms so the Win32 HWND is fully activated before FindWindow.
     */
    public void onWindowRestored() {
        var delay = new PauseTransition(Duration.millis(100));
        delay.setOnFinished(e -> doWakeup());
        delay.play();
    }

    /**
     * Call when the host window is hidden (tray hide or hotkey hide).
     * Belt-and-suspenders alongside the showingProperty listener.
     */
    public void onWindowHidden() {
        bridge.hibernate();
    }

    private synchronized void startBridgeIfReady() {
        if (bridgeStarted) {
            return;
        }
        var scene = getScene();
        if (scene == null) {
            return;
        }
        var window = scene.getWindow();
        if (window == null || !window.isShowing()) {
            return;
        }

        long parentHandle = resolveParentHandle(window);
        if (parentHandle == 0L && SystemUtils.isWindows()) {
            scheduleRetryStart();
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
        boolean temporary = title == null || title.isEmpty();
        if (temporary) {
            title = "SparkMainWindow-" + System.nanoTime();
            stage.setTitle(title);
        }

        long handle = NativeWindowUtils.getJavaFXWindowHandle(title);
        if (temporary) {
            stage.setTitle("");
        }
        return handle;
    }

    private void scheduleRetryStart() {
        var t = new PauseTransition(Duration.millis(50));
        t.setOnFinished(e -> startBridgeIfReady());
        t.play();
    }

    private void doWakeup() {
        var window = getScene() != null ? getScene().getWindow() : null;
        if (window == null || !window.isShowing()) {
            return;
        }
        long parentHandle = resolveParentHandle(window);
        if (parentHandle == 0L && SystemUtils.isWindows()) {
            var t = new PauseTransition(Duration.millis(50));
            t.setOnFinished(e -> doWakeup());
            t.play();
            return;
        }

        log.info("FxWebViewPane: Waking up bridge with parentHandle=0x{}", Long.toHexString(parentHandle));
        bridge.wakeup(parentHandle);
        getScene().getRoot().applyCss();
        getScene().getRoot().layout();
        syncBounds();
    }

    void syncBounds() {
        if (!bridgeStarted || getScene() == null || getScene().getWindow() == null) {
            return;
        }
        var window = getScene().getWindow();
        var scene = getScene();
        var bounds = localToScene(getBoundsInLocal());
        if (bounds == null) {
            return;
        }

        int x, y, w, h;
        if (SystemUtils.isWindows()) {
            var sx = window.getOutputScaleX();
            var sy = window.getOutputScaleY();
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

    /**
     * Navigates the webview to the given AI provider.
     * Shows a loading overlay while the page transitions.
     */
    public void setCurrentConfig(AiConfiguration.AiConfig config) {
        var icon = AiDock.ICON_CACHE.get(config.icon());

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
        bridge.clearCookies();
    }

    public void resetZoom() {
        bridge.resetZoom();
    }

    public void restart() {
        var url = appPreferences.getLastUrl() != null ? appPreferences.getLastUrl() : startUrl;
        bridge.navigate(url);
    }

    public void shutdown(Runnable onComplete) {
        bridge.shutdown(() -> Platform.runLater(() -> {
            onComplete.run();
            System.exit(0);
        }));
    }
}
