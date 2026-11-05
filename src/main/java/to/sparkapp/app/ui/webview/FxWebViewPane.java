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
import javafx.stage.Window;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.util.Duration;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import to.sparkapp.app.browser.WebviewManager;
import to.sparkapp.app.browser.WebviewNavigator;
import to.sparkapp.app.config.AiConfiguration;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.ui.Theme;
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

    private WebviewManager bridge;
    private final Map<String, WebviewManager> sessions = new HashMap<>();
    private final Map<WebviewManager, AiConfiguration.AiConfig> sites = new HashMap<>();
    private final Set<WebviewManager> started = new HashSet<>();
    private boolean initialBrowserUnused = true;
    private boolean clearingData;
    private boolean disposed;
    private long restoreStarted;
    private final PauseTransition wakeupRetry = new PauseTransition(Duration.millis(30));
    private int wakeupAttempts;
    private final PauseTransition loadTimeout = new PauseTransition(Duration.seconds(20));
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
        var source = bridge;
        source.setOnReadyCallback(() -> Platform.runLater(() -> {
            if (disposed || bridge != source) return;
            syncBounds();
            showLoadState();
        }));
        source.setOnLoadingChanged(loading -> {
            if (!disposed && bridge == source) showLoadState();
        });
        source.setOnLoadError(error -> {
            if (!disposed && bridge == source) showLoadState();
        });
        source.setZoomCallback(pct -> {
            if (bridge == source && zoomCallback != null) zoomCallback.accept(pct);
        });
        source.setOnUrlChanged(url -> {
            if (disposed) return;
            boolean auth = WebviewNavigator.isAuthUrl(url);
            if (bridge == source && onAuthPageDetected != null) onAuthPageDetected.accept(auth);
            var site = sites.get(source);
            if (site != null && !auth) appPreferences.rememberSitePage(site, url, bridge == source);
        });
    }

    private boolean windowVisible() {
        return !disposed && getScene() != null && getScene().getWindow() != null
                && getScene().getWindow().isShowing();
    }

    private void showLoadState() {
        loadTimeout.stop();
        if (!windowVisible()) return;
        if (bridge.getLoadError() != null) {
            bridge.setVisible(false);
            overlay.showError(bridge.getLoadError(), this::retryPage);
        } else if (bridge.isLoading()) {
            bridge.setVisible(false);
            overlay.activate(null, 0, null);
            loadTimeout.setOnFinished(e -> {
                if (!windowVisible() || !bridge.isLoading()) return;
                overlay.showError("Loading is taking longer than expected. You can retry or show the page.",
                        this::retryPage, () -> { overlay.deactivate(); bridge.setVisible(true); bridge.focusBrowser(); });
            });
            loadTimeout.playFromStart();
        } else {
            overlay.deactivate();
            syncBounds();
            bridge.setVisible(true);
            bridge.focusBrowser();
        }
    }

    private void retryPage() {
        bridge.reload();
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

    private void attachWindowListeners(Window window) {
        if (disposed || window == null || windowListenersAttached) {
            return;
        }
        windowListenersAttached = true;

        window.showingProperty().addListener((o, old, isShowing) -> {
            if (isShowing) {
                if (!bridgeStarted) {
                    startBridgeIfReady();
                } else {
                    onWindowRestored();
                }
                startHostGeometryWatchdog();
            } else {
                onWindowHidden();
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
     * Reuses the known HWND immediately; retries only if the host is not available.
     */
    public void onWindowRestored() { onWindowRestored(System.nanoTime()); }

    public void onWindowRestored(long requestedAt) {
        restoreStarted = requestedAt;
        wakeupAttempts = 0;
        wakeupRetry.stop();
        doWakeup();
    }

    public void onWindowHidden() {
        overlay.pauseAnimation();
        wakeupRetry.stop();
        loadTimeout.stop();
        hostGeometryWatchdog.stop();
        bridge.hibernate();
        parentHandle = 0L;
    }

    private synchronized void startBridgeIfReady() {
        if (disposed || bridgeStarted) return;
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
        started.add(bridge);
        bridge.init(startUrl, handle, initialBounds[0], initialBounds[1], initialBounds[2], initialBounds[3]);
        bridge.wakeup(handle);
        resyncBounds();
        startHostGeometryWatchdog();
    }

    private long resolveParentHandle(Window window) {
        if (!SystemUtils.isWindows() || !(window instanceof Stage stage)) {
            return 0L;
        }
        
        var originalTitle = stage.getTitle();
        var uniqueTitle = "SparkWindow-" + UUID.randomUUID();
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
        if (disposed || window == null || !window.isShowing()) {
            return;
        }
        if (!bridgeStarted) { startBridgeIfReady(); return; }
        long handle = parentHandle != 0 ? parentHandle : resolveParentHandle(window);
        if (handle == 0L && SystemUtils.isWindows()) {
            if (++wakeupAttempts <= 20) {
                wakeupRetry.setOnFinished(e -> doWakeup());
                wakeupRetry.playFromStart();
            }
            return;
        }
        this.parentHandle = handle;

        log.info("FxWebViewPane: Waking up bridge with parentHandle=0x{}", Long.toHexString(handle));
        bridge.wakeup(handle);
        getScene().getRoot().applyCss();
        getScene().getRoot().layout();
        resyncBounds();
        startHostGeometryWatchdog();
        showLoadState();
        long requestedAt = restoreStarted;
        bridge.focusBrowser(accepted -> log.info("Browser native focus completed in {} ms (accepted={})",
                (System.nanoTime() - requestedAt) / 1_000_000, accepted));
        log.info("Browser restore dispatched in {} ms (warm={})",
                (System.nanoTime() - restoreStarted) / 1_000_000, !bridge.isLoading());
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
    private double[] resolveHostScale(Window window) {
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
        if (disposed || clearingData) return;
        var existing = sessions.get(config.id());
        var previousConfig = existing == null ? null : sites.get(existing);
        if (previousConfig != null && !previousConfig.url().equals(config.url())) {
            existing.shutdown(() -> {});
            started.remove(existing);
            sites.remove(existing);
            sessions.remove(config.id());
            existing = null;
        }
        if (existing == bridge) return;
        bridge.hibernate();
        if (existing == null) {
            if (!initialBrowserUnused) bridge = new WebviewManager(appPreferences);
            initialBrowserUnused = false;
            sessions.put(config.id(), bridge);
            sites.put(bridge, config);
            setupBridgeCallbacks();
            var remembered = targetUrl != null ? targetUrl : appPreferences.getSiteUrl(config);
            bridge.setCurrentConfig(config, remembered);
        } else {
            bridge = existing;
        }
        bridgeStarted = started.contains(bridge);
        var url = targetUrl != null ? targetUrl : appPreferences.getSiteUrl(config);
        appPreferences.rememberSitePage(config, url != null ? url : config.url(), true);
        if (windowVisible()) {
            if (!bridgeStarted) startBridgeIfReady();
            else onWindowRestored();
            showLoadState();
        }
    }

    public void reconcileSites(List<AiConfiguration.AiConfig> configurations) {
        sessions.entrySet().removeIf(entry -> {
            var site = sites.get(entry.getValue());
            boolean removed = configurations.stream().noneMatch(config -> config.id().equals(entry.getKey())
                    && config.url().equals(site.url()));
            if (removed) {
                var browser = entry.getValue();
                browser.hibernate();
                browser.shutdown(() -> {});
                sites.remove(browser);
                started.remove(browser);
            }
            return removed;
        });
    }

    public void clearCookies() {
        if (clearingData || !bridgeStarted || disposed) return;
        var confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                "Clear cookies and website storage for all sites in Spark? This signs you out and reloads open sites. Unsent text will be lost.",
                ButtonType.CANCEL, ButtonType.OK);
        confirmation.initOwner(getScene().getWindow());
        confirmation.setTitle("Clear site data");
        confirmation.setHeaderText("Sign out of all sites");
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        clearingData = true;
        sessions.values().forEach(WebviewManager::stopLoading);
        bridge.clearBrowsingData(success -> {
            clearingData = false;
            if (disposed) return;
            if (success) {
                appPreferences.clearSiteHistory();
                sessions.values().forEach(WebviewManager::reload);
            }
            var result = new Alert(success ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR);
            result.initOwner(getScene().getWindow());
            result.setTitle("Clear site data");
            result.setHeaderText(success ? "Site data cleared" : "Site data could not be cleared");
            result.setContentText(success ? "Cookies and stored website data have been cleared for the Spark browser profile."
                    : "Your data has not been reported as cleared. Please try again or check the application log.");
            result.show();
        });
    }

    public void resetZoom() { bridge.resetZoom(); }

    public void shutdown(Runnable onComplete) {
        overlay.deactivate();
        disposed = true;
        wakeupRetry.stop();
        loadTimeout.stop();
        hostGeometryWatchdog.stop();
        var browsers = new HashSet<>(sessions.values());
        browsers.add(bridge);
        var remaining = new AtomicInteger(browsers.size());
        browsers.forEach(browser -> browser.shutdown(() -> {
            if (remaining.decrementAndGet() == 0) Platform.runLater(onComplete);
        }));
    }
}
