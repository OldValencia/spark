package to.sparkapp.app.ui;

import javafx.animation.Animation;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import to.sparkapp.app.browser.WebviewManager;
import to.sparkapp.app.config.AiConfiguration;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.ui.topbar.components.AiDock;
import to.sparkapp.app.utils.NativeWindowUtils;
import to.sparkapp.app.utils.SystemUtils;

import java.util.function.Consumer;

@Slf4j
public class FxWebViewPane extends StackPane {

    private final WebviewManager bridge;
    private final AppPreferences appPreferences;
    private final Runnable onToggleSettings;
    private final String startUrl;

    @Setter
    private Consumer<Double> zoomCallback;
    @Setter
    private Consumer<Boolean> onAuthPageDetected;

    private boolean bridgeStarted = false;

    private final VBox loadingOverlay;
    private final ImageView loadingIcon;
    private PauseTransition loadingTimer;
    private final ScaleTransition iconPulse;

    public FxWebViewPane(String startUrl,
                         AppPreferences appPreferences,
                         Runnable onToggleSettings) {
        this.startUrl = startUrl;
        this.appPreferences = appPreferences;
        this.onToggleSettings = onToggleSettings;

        this.setPadding(new Insets(0));
        this.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        this.setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        this.setStyle(String.format("-fx-background-color: %s;", Theme.toHex(Theme.BG_DEEP)));

        loadingIcon = new ImageView();
        loadingIcon.setFitWidth(32);
        loadingIcon.setFitHeight(32);
        loadingIcon.setPreserveRatio(true);
        loadingIcon.setSmooth(true);

        var glow = new DropShadow();
        glow.setColor(Color.web("#FFFFFF66"));
        glow.setRadius(15);
        loadingIcon.setEffect(glow);

        loadingOverlay = new VBox(loadingIcon);
        loadingOverlay.setAlignment(Pos.CENTER);
        loadingOverlay.setStyle(String.format("-fx-background-color: %s;", Theme.toHex(Theme.BG_DEEP)));
        loadingOverlay.setVisible(true);

        iconPulse = new ScaleTransition(javafx.util.Duration.millis(600), loadingIcon);
        iconPulse.setFromX(0.9);
        iconPulse.setFromY(0.9);
        iconPulse.setToX(1.2);
        iconPulse.setToY(1.2);
        iconPulse.setAutoReverse(true);
        iconPulse.setCycleCount(Animation.INDEFINITE);

        this.getChildren().add(loadingOverlay);

        bridge = new WebviewManager(appPreferences);

        bridge.setOnReadyCallback(() -> Platform.runLater(() -> {
            syncBounds();
            bridge.setVisible(true);

            if (loadingOverlay.isVisible()) {
                iconPulse.stop();
                loadingOverlay.setVisible(false);
                if (loadingTimer != null) {
                    loadingTimer.stop();
                    loadingTimer = null;
                }
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

        this.boundsInLocalProperty().addListener((obs, oldVal, newVal) -> {
            if (!bridgeStarted) {
                startBridgeIfNeeded();
            } else {
                syncBounds();
            }
        });

        this.localToSceneTransformProperty().addListener((obs, oldVal, newVal) -> syncBounds());

        this.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.windowProperty().addListener((wObs, oldWin, newWin) -> {
                    if (newWin != null) {
                        if (SystemUtils.isMac()) {
                            newWin.xProperty().addListener((o, old, val) -> syncBounds());
                            newWin.yProperty().addListener((o, old, val) -> syncBounds());
                        }

                        newWin.showingProperty().addListener((o, old, isShowing) -> {
                            if (isShowing) {
                                if (!bridgeStarted) {
                                    startBridgeIfNeeded();
                                } else {
                                    wakeupBridge();
                                }
                            } else {
                                if (bridgeStarted) {
                                    bridge.hibernate();
                                }
                            }
                        });
                    }
                });
            }
        });
    }

    private void wakeupBridge() {
        var window = getScene().getWindow();
        if (window == null || !window.isShowing()) return;

        long parentHandle = 0L;
        if (SystemUtils.isWindows() && window instanceof Stage stage) {
            var title = stage.getTitle();
            if (title == null || title.isEmpty()) {
                title = "SparkMainWindow-" + System.nanoTime();
                stage.setTitle(title);
            }
            parentHandle = NativeWindowUtils.getJavaFXWindowHandle(title);
            if (title.startsWith("SparkMainWindow-")) {
                stage.setTitle("");
            }

            if (parentHandle == 0L) {
                var pause = new javafx.animation.PauseTransition(javafx.util.Duration.millis(50));
                pause.setOnFinished(e -> wakeupBridge());
                pause.play();
                return;
            }
        }

        bridge.wakeup(parentHandle);
        syncBounds();
    }

    public void resetZoom() {
        if (bridge != null) bridge.resetZoom();
    }

    private synchronized void startBridgeIfNeeded() {
        if (bridgeStarted) return;

        var scene = getScene();
        if (scene == null) return;
        var window = scene.getWindow();
        if (window == null || !window.isShowing()) return;

        long parentHandle = 0L;
        if (SystemUtils.isWindows() && window instanceof Stage stage) {
            var title = stage.getTitle();
            if (title == null || title.isEmpty()) {
                title = "SparkMainWindow-" + System.nanoTime();
                stage.setTitle(title);
            }

            parentHandle = NativeWindowUtils.getJavaFXWindowHandle(title);

            if (title.startsWith("SparkMainWindow-")) {
                stage.setTitle("");
            }

            if (parentHandle == 0L) {
                var pause = new javafx.animation.PauseTransition(javafx.util.Duration.millis(50));
                pause.setOnFinished(e -> startBridgeIfNeeded());
                pause.play();
                return;
            }
        }

        bridgeStarted = true;

        bridge.init(startUrl, parentHandle, 0, 0, 10, 10);
        Platform.runLater(this::syncBounds);
    }

    private void syncBounds() {
        if (!bridgeStarted || getScene() == null || getScene().getWindow() == null || bridge == null) return;

        var window = getScene().getWindow();
        var scene = getScene();
        var boundsInScene = localToScene(getBoundsInLocal());
        if (boundsInScene == null) return;

        int w, h, x, y;

        if (SystemUtils.isWindows()) {
            double scaleX = window.getOutputScaleX();
            double scaleY = window.getOutputScaleY();
            x = (int) Math.round((boundsInScene.getMinX() + scene.getX()) * scaleX);
            y = (int) Math.round((boundsInScene.getMinY() + scene.getY()) * scaleY);
            w = (int) Math.round(boundsInScene.getWidth() * scaleX);
            h = (int) Math.round(boundsInScene.getHeight() * scaleY);
        } else {
            x = (int) Math.round(boundsInScene.getMinX() + scene.getX());
            y = (int) Math.round(boundsInScene.getMinY() + scene.getY());
            w = (int) Math.round(boundsInScene.getWidth());
            h = (int) Math.round(boundsInScene.getHeight());
        }

        bridge.updateBounds(x, y, w, h);
    }

    private Bounds getBoundsInScreen() {
        if (getScene() == null || getScene().getWindow() == null) return null;
        return localToScreen(getBoundsInLocal());
    }

    public void setCurrentConfig(AiConfiguration.AiConfig config) {
        if (loadingTimer != null) {
            loadingTimer.stop();
            loadingTimer = null;
        }

        var image = AiDock.ICON_CACHE.get(config.icon());
        loadingIcon.setImage(image);

        loadingOverlay.toFront();
        loadingOverlay.setVisible(true);
        iconPulse.play();
        bridge.setVisible(false);

        bridge.setCurrentConfig(config);

        loadingTimer = new PauseTransition(javafx.util.Duration.millis(1200));
        loadingTimer.setOnFinished(e -> {
            syncBounds();
            bridge.setVisible(true);
            iconPulse.stop();
            loadingOverlay.setVisible(false);
            loadingTimer = null;
        });
        loadingTimer.play();
    }

    public void clearCookies() {
        bridge.clearCookies();
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

    private void checkIfAuthPage(String url) {
        if (url == null) return;
        var lower = url.toLowerCase();
        var isAuth = lower.contains("accounts.google.com")
                || lower.contains("appleid.apple.com")
                || lower.contains("github.com/login")
                || lower.contains("oauth")
                || lower.contains("signin")
                || lower.contains("login");

        if (onAuthPageDetected != null) {
            Platform.runLater(() -> onAuthPageDetected.accept(isAuth));
        }
    }
}
