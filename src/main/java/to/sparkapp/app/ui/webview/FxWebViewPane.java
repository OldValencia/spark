package to.sparkapp.app.ui.webview;

import java.util.List;
import java.util.function.Consumer;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Window;
import javafx.util.Duration;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import to.sparkapp.app.browser.WebviewManager;
import to.sparkapp.app.browser.WebviewNavigator;
import to.sparkapp.app.config.AiConfiguration.AiConfig;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.ui.Theme;
import to.sparkapp.app.utils.SystemUtils;

/**
 * The main JavaFX pane that hosts the native WebView browser.
 *
 * <p>The native webview is a separate OS window that is parented and positioned to perfectly
 * overlap this pane. A {@link WebViewLoadingOverlay} is shown above while navigation is in
 * progress.
 */
@Slf4j
public class FxWebViewPane extends StackPane {

    private WebviewManager bridge;
    private final BrowserSessions sessions;
    private final SiteDataClearing dataClearing;
    private boolean disposed;
    private long restoreStarted;
    private final PauseTransition wakeupRetry = new PauseTransition(Duration.millis(30));
    private int wakeupAttempts;

    private final AppPreferences appPreferences;
    private final String startUrl;
    @Getter
    private String activeSiteId;
    private final BrowserLoadingView loadingView;

    private boolean bridgeStarted = false;
    private boolean windowListenersAttached = false;

    private final BrowserViewport viewport;

    private Consumer<Double> zoomCallback;
    @Setter
    private Consumer<Boolean> onAuthPageDetected;

    public FxWebViewPane(String startUrl, AppPreferences appPreferences) {
        this.startUrl = startUrl;
        this.appPreferences = appPreferences;
        this.sessions = new BrowserSessions(appPreferences);
        this.bridge = sessions.getActive();

        setPadding(Insets.EMPTY);
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        setStyle("-fx-background-color: " + Theme.toHex(Theme.BG_DEEP) + ";");

        loadingView = new BrowserLoadingView(() -> bridge, this::windowVisible, this::syncBounds);
        getChildren().add(loadingView.getOverlay());
        dataClearing = new SiteDataClearing(sessions, appPreferences, () -> getScene().getWindow(), () -> disposed);

        viewport = new BrowserViewport(this, () -> bridge, () -> bridgeStarted);

        setupBridgeCallbacks(bridge);
        setupLayoutListeners();
    }

    private void setupBridgeCallbacks(WebviewManager source) {
        source.setOnReadyCallback(() -> Platform.runLater(() -> {
            if (disposed || bridge != source) {
                return;
            }

            syncBounds();
            loadingView.refresh();
        }));
        source.setOnLoadingChanged(loading -> {
            if (!disposed && bridge == source) {
                loadingView.refresh();
            }
        });
        source.setOnLoadError(error -> {
            if (!disposed && bridge == source) {
                loadingView.refresh();
            }
        });
        source.setZoomCallback(pct -> {
            if (bridge == source && zoomCallback != null) {
                zoomCallback.accept(pct);
            }
        });
        source.setOnUrlChanged(url -> {
            if (disposed) {
                return;
            }

            boolean auth = WebviewNavigator.isAuthUrl(url);

            if (bridge == source && onAuthPageDetected != null) {
                onAuthPageDetected.accept(auth);
            }

            var site = sessions.findSite(source);

            if (site != null && !auth) {
                appPreferences.rememberSitePage(site, url, bridge == source);
            }
        });
    }

    private boolean windowVisible() {
        return !disposed && getScene() != null && getScene().getWindow() != null && getScene().getWindow().isShowing();
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

                viewport.start();
            } else {
                onWindowHidden();
            }
        });

        window.xProperty().addListener((o, old, n) -> syncBounds());
        window.yProperty().addListener((o, old, n) -> syncBounds());
        window.widthProperty().addListener((o, old, n) -> syncBounds());
        window.heightProperty().addListener((o, old, n) -> syncBounds());
        window.outputScaleXProperty().addListener((o, old, n) -> viewport.resync());
        window.outputScaleYProperty().addListener((o, old, n) -> viewport.resync());
        window.renderScaleXProperty().addListener((o, old, n) -> viewport.resync());
        window.renderScaleYProperty().addListener((o, old, n) -> viewport.resync());

        if (window.isShowing()) {
            viewport.start();
        }
    }

    /**
     * Call after the host window becomes visible (tray restore or hotkey show). Reuses the known
     * HWND immediately; retries only if the host is not available.
     */
    public void onWindowRestored() {
        onWindowRestored(System.nanoTime());
    }

    public void onWindowRestored(long requestedAt) {
        restoreStarted = requestedAt;
        wakeupAttempts = 0;
        wakeupRetry.stop();
        doWakeup();
    }

    public void onWindowHidden() {
        loadingView.pause();
        wakeupRetry.stop();
        viewport.stop();
        bridge.hibernate();
        viewport.setParentHandle(0L);
    }

    private synchronized void startBridgeIfReady() {
        if (disposed || bridgeStarted) {
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

        long handle = BrowserBounds.resolveParentHandle(window);

        if (handle == 0L && SystemUtils.isWindows()) {
            scheduleRetryStart();

            return;
        }

        viewport.setParentHandle(handle);

        int[] initialBounds = viewport.currentBounds();
        bridgeStarted = true;
        sessions.markStarted(bridge);
        bridge.init(startUrl, handle, initialBounds[0], initialBounds[1], initialBounds[2], initialBounds[3]);
        bridge.wakeup(handle);
        viewport.resync();
        viewport.start();
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

        if (!bridgeStarted) {
            startBridgeIfReady();

            return;
        }

        long handle = viewport.getParentHandle() != 0 ? viewport.getParentHandle() : BrowserBounds.resolveParentHandle(
                window);

        if (handle == 0L && SystemUtils.isWindows()) {
            if (++wakeupAttempts <= 20) {
                wakeupRetry.setOnFinished(e -> doWakeup());
                wakeupRetry.playFromStart();
            }

            return;
        }

        viewport.setParentHandle(handle);

        log.info("FxWebViewPane: Waking up bridge with parentHandle=0x{}", Long.toHexString(handle));
        bridge.wakeup(handle);
        getScene().getRoot().applyCss();
        getScene().getRoot().layout();
        viewport.resync();
        viewport.start();
        loadingView.refresh();

        long requestedAt = restoreStarted;
        bridge.focusBrowser(accepted -> log.info("Browser native focus completed in {} ms (accepted={})",
                (System.nanoTime() - requestedAt) / 1_000_000, accepted));
        log.info("Browser restore dispatched in {} ms (warm={})", (System.nanoTime() - restoreStarted) / 1_000_000,
                !bridge.isLoading());
    }

    void syncBounds() {
        viewport.syncBounds();
    }

    /**
     * Navigates the webview to the given AI provider, or to {@code targetUrl} when it points at a
     * page of that provider. Shows a loading overlay while the page transitions.
     */
    public void setCurrentConfig(AiConfig config, String targetUrl) {
        if (disposed || dataClearing.isClearing()) {
            return;
        }

        activeSiteId = config.id();

        if (!sessions.select(config, targetUrl, this::setupBridgeCallbacks)) {
            return;
        }

        bridge = sessions.getActive();

        if (zoomCallback != null) {
            zoomCallback.accept(bridge.getZoomPercent());
        }

        bridgeStarted = sessions.isStarted(bridge);

        var url = targetUrl != null ? targetUrl : appPreferences.getSiteUrl(config);
        appPreferences.rememberSitePage(config, url != null ? url : config.url(), true);

        if (windowVisible()) {
            if (!bridgeStarted) {
                startBridgeIfReady();
            } else {
                onWindowRestored();
            }

            loadingView.refresh();
        }
    }

    public void reconcileSites(List<AiConfig> configurations) {
        sessions.reconcileSites(configurations);
    }

    public void setZoomCallback(Consumer<Double> callback) {
        zoomCallback = callback;

        if (callback != null) {
            callback.accept(bridge.getZoomPercent());
        }
    }

    public void clearCookies() {
        if (!dataClearing.isClearing() && bridgeStarted && !disposed) {
            dataClearing.clear(bridge);
        }
    }

    public void refreshResourcePolicy() {
        sessions.forEach(WebviewManager::refreshResourcePolicy);
    }

    public void resetZoom() {
        bridge.resetZoom();
    }

    public void zoomIn() {
        bridge.zoomIn();
    }

    public void zoomOut() {
        bridge.zoomOut();
    }

    public void shutdown(Runnable onComplete) {
        loadingView.stop();
        disposed = true;
        wakeupRetry.stop();
        viewport.stop();

        sessions.shutdown(onComplete);
    }
}
