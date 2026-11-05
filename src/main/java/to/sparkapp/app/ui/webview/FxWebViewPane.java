package to.sparkapp.app.ui.webview;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
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
    private boolean windowListenersAttached = false;

    /**
     * HWND of the hosting JavaFX window. Cached because resolving it renames the
     * stage temporarily, which is too expensive to redo on every layout pass.
     */
    private long parentHandle = 0L;

    /** Last host geometry seen by {@link #checkHostGeometry()}, in physical pixels. */
    private int lastHostClientWidth = -1;
    private int lastHostClientHeight = -1;
    private int lastHostDpi = -1;

    /**
     * JavaFX does not reliably notify us when the host window is dragged to a
     * monitor with a different scale factor, so the host geometry is polled as a
     * safety net. Without this the native webview keeps the physical size it had
     * on the previous monitor and ends up as a small rectangle inside the window.
     */
    private final Timeline hostGeometryWatchdog = new Timeline(
            new KeyFrame(Duration.millis(400), e -> checkHostGeometry()));

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

        hostGeometryWatchdog.setCycleCount(Animation.INDEFINITE);

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
            if (appPreferences.isRememberLastAi() && !WebviewNavigator.isAuthUrl(url)) {
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
            newScene.windowProperty().addListener((wObs, oldWin, newWin) -> attachWindowListeners(newWin));

            // The scene usually already belongs to a stage by the time this pane is
            // added to it, in which case windowProperty never fires again.
            attachWindowListeners(newScene.getWindow());

            if (newScene.getWindow() != null && newScene.getWindow().isShowing() && !bridgeStarted) {
                startBridgeIfReady();
            }
        });
    }

    private void attachWindowListeners(javafx.stage.Window window) {
        if (window == null || windowListenersAttached) {
            return;
        }
        windowListenersAttached = true;

        window.showingProperty().addListener((o, old, isShowing) -> {
            if (isShowing) {
                if (!bridgeStarted) {
                    startBridgeIfReady();
                } else {
                    resyncBounds();
                }
                startHostGeometryWatchdog();
            } else {
                hostGeometryWatchdog.stop();
                if (bridgeStarted) {
                    bridge.hibernate();
                }
            }
        });

        window.xProperty().addListener((o, old, n) -> syncBounds());
        window.yProperty().addListener((o, old, n) -> syncBounds());
        window.widthProperty().addListener((o, old, n) -> syncBounds());
        window.heightProperty().addListener((o, old, n) -> syncBounds());
        window.outputScaleXProperty().addListener((o, old, n) -> resyncBounds());
        window.outputScaleYProperty().addListener((o, old, n) -> resyncBounds());
        window.renderScaleXProperty().addListener((o, old, n) -> resyncBounds());
        window.renderScaleYProperty().addListener((o, old, n) -> resyncBounds());

        if (window.isShowing()) {
            startHostGeometryWatchdog();
        }
    }

    private void startHostGeometryWatchdog() {
        if (SystemUtils.isWindows() && hostGeometryWatchdog.getStatus() != Animation.Status.RUNNING) {
            hostGeometryWatchdog.play();
        }
    }

    /**
     * Re-applies the native bounds now and again shortly after: on a DPI change the
     * host window is resized by the platform after the scale properties change, so a
     * single immediate pass would compute the rectangle against a stale window size.
     */
    private void resyncBounds() {
        syncBounds();
        Platform.runLater(this::syncBounds);
        for (int delayMs : new int[]{80, 250, 600}) {
            var delay = new PauseTransition(Duration.millis(delayMs));
            delay.setOnFinished(e -> syncBounds());
            delay.play();
        }
    }

    /**
     * Detects host window resizes and monitor scale changes that JavaFX did not
     * report, and re-syncs the native webview when they happen.
     */
    private void checkHostGeometry() {
        if (!bridgeStarted || parentHandle == 0) {
            return;
        }
        var window = getScene() != null ? getScene().getWindow() : null;
        if (window == null || !window.isShowing()) {
            return;
        }

        int[] client = NativeWindowUtils.getClientSize(parentHandle);
        if (client == null) {
            return;
        }
        int dpi = NativeWindowUtils.getWindowDpi(parentHandle);

        if (client[0] != lastHostClientWidth || client[1] != lastHostClientHeight || dpi != lastHostDpi) {
            lastHostClientWidth = client[0];
            lastHostClientHeight = client[1];
            lastHostDpi = dpi;

            applyCss();
            layout();
            syncBounds();
        }
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
        hostGeometryWatchdog.stop();
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

        long handle = resolveParentHandle(window);
        if (handle == 0L && SystemUtils.isWindows()) {
            scheduleRetryStart();
            return;
        }
        this.parentHandle = handle;

        int[] initialBounds = calculateCurrentBounds();
        bridgeStarted = true;
        bridge.init(startUrl, handle, initialBounds[0], initialBounds[1], initialBounds[2], initialBounds[3]);
        resyncBounds();
        startHostGeometryWatchdog();
    }

    private long resolveParentHandle(javafx.stage.Window window) {
        if (!SystemUtils.isWindows() || !(window instanceof Stage stage)) {
            return 0L;
        }
        
        var originalTitle = stage.getTitle();
        var uniqueTitle = "SparkWindow-" + java.util.UUID.randomUUID();
        stage.setTitle(uniqueTitle);
        
        long handle = NativeWindowUtils.getJavaFXWindowHandle(uniqueTitle);
        
        stage.setTitle(originalTitle != null ? originalTitle : "");
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
        long handle = resolveParentHandle(window);
        if (handle == 0L && SystemUtils.isWindows()) {
            var t = new PauseTransition(Duration.millis(50));
            t.setOnFinished(e -> doWakeup());
            t.play();
            return;
        }
        this.parentHandle = handle;

        log.info("FxWebViewPane: Waking up bridge with parentHandle=0x{}", Long.toHexString(handle));
        bridge.wakeup(handle);
        getScene().getRoot().applyCss();
        getScene().getRoot().layout();
        resyncBounds();
        startHostGeometryWatchdog();
    }

    void syncBounds() {
        if (!bridgeStarted || getScene() == null || getScene().getWindow() == null) {
            return;
        }
        int[] b = calculateCurrentBounds();
        if (b[2] <= 0 || b[3] <= 0) {
            return;
        }
        bridge.updateBounds(b[0], b[1], b[2], b[3]);
    }

    private int[] calculateCurrentBounds() {
        if (getScene() == null || getScene().getWindow() == null) {
            return new int[]{0, 0, (int) Math.round(getWidth()), (int) Math.round(getHeight())};
        }
        var window = getScene().getWindow();
        var scene = getScene();
        var bounds = localToScene(getBoundsInLocal());
        if (bounds == null || bounds.getWidth() <= 0 || bounds.getHeight() <= 0) {
            return new int[]{0, 0, (int) Math.round(getWidth()), (int) Math.round(getHeight())};
        }

        int x, y, w, h;
        if (SystemUtils.isWindows()) {
            var scale = resolveHostScale(window);
            var sx = scale[0];
            var sy = scale[1];
            x = (int) Math.round(bounds.getMinX() * sx);
            y = (int) Math.round(bounds.getMinY() * sy);
            w = (int) Math.round(bounds.getWidth() * sx);
            h = (int) Math.round(bounds.getHeight() * sy);

            // Never let the webview stick out of the host window's client area.
            int[] client = NativeWindowUtils.getClientSize(parentHandle);
            if (client != null && client[0] > 0 && client[1] > 0) {
                w = Math.min(w, client[0] - x);
                h = Math.min(h, client[1] - y);
            }
        } else {
            x = (int) Math.round(bounds.getMinX() + scene.getX());
            y = (int) Math.round(bounds.getMinY() + scene.getY());
            w = (int) Math.round(bounds.getWidth());
            h = (int) Math.round(bounds.getHeight());
        }
        return new int[]{x, y, w, h};
    }

    /**
     * Scale factor between JavaFX logical units and physical pixels.
     *
     * <p>{@code outputScaleX/Y} is used when it matches reality, but it can lag behind
     * (or never update at all) when the window is dragged onto a monitor with a
     * different scale factor, so the host window's real client size wins when the
     * two disagree.
     */
    private double[] resolveHostScale(javafx.stage.Window window) {
        var sx = window.getOutputScaleX();
        var sy = window.getOutputScaleY();

        int[] client = NativeWindowUtils.getClientSize(parentHandle);
        if (client == null || window.getWidth() <= 0 || window.getHeight() <= 0) {
            return new double[]{sx, sy};
        }

        var realX = client[0] / window.getWidth();
        var realY = client[1] / window.getHeight();
        if (isSaneScale(realX) && isSaneScale(realY)
                && (Math.abs(realX - sx) > 0.01 || Math.abs(realY - sy) > 0.01)) {
            return new double[]{realX, realY};
        }
        return new double[]{sx, sy};
    }

    private static boolean isSaneScale(double scale) {
        return scale >= 0.5 && scale <= 8.0;
    }

    /**
     * Navigates the webview to the given AI provider, or to {@code targetUrl} when it
     * points at a page of that provider. Shows a loading overlay while the page
     * transitions.
     */
    public void setCurrentConfig(AiConfiguration.AiConfig config, String targetUrl) {
        var icon = AiDock.ICON_CACHE.get(config.icon());

        if (!bridgeStarted) {
            overlay.activate(icon, 0, null);
            bridge.setCurrentConfig(config, targetUrl);
            return;
        }

        bridge.setVisible(false);
        overlay.activate(icon, 1200, () -> {
            syncBounds();
            bridge.setVisible(true);
        });

        bridge.setCurrentConfig(config, targetUrl);
    }

    public void clearCookies() {
        bridge.clearCookies();
    }

    public void resetZoom() {
        bridge.resetZoom();
    }

    public void shutdown(Runnable onComplete) {
        hostGeometryWatchdog.stop();
        bridge.shutdown(() -> Platform.runLater(onComplete));
    }
}
