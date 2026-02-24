package io.loom.app.ui;

import io.loom.app.browser.NativeWebViewBridge;
import io.loom.app.config.AiConfiguration;
import io.loom.app.config.AppPreferences;
import io.loom.app.utils.NativeWindowUtils;
import io.loom.app.utils.SystemUtils;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.layout.Region;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;

@Slf4j
public class FxWebViewPane extends Region {

    private final NativeWebViewBridge bridge;
    private final AppPreferences appPreferences;
    private final Runnable onToggleSettings;
    private final String startUrl;

    @Setter
    private Consumer<Double> zoomCallback;
    @Setter
    private Consumer<Boolean> onAuthPageDetected;

    private boolean bridgeStarted = false;

    public FxWebViewPane(String startUrl,
                         AppPreferences appPreferences,
                         Runnable onToggleSettings) {
        this.startUrl = startUrl;
        this.appPreferences = appPreferences;
        this.onToggleSettings = onToggleSettings;

        this.setStyle(String.format("-fx-background-color: %s;", Theme.toHex(Theme.BG_DEEP)));

        bridge = new NativeWebViewBridge(appPreferences);

        bridge.setZoomCallback(pct -> {
            if (zoomCallback != null) {
                zoomCallback.accept(pct);
            }
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
                                    // Просыпаемся из трея
                                    wakeupBridge();
                                }
                            } else {
                                if (bridgeStarted) {
                                    // Прячемся в трей без смерти процесса
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
        if (SystemUtils.isWindows() && window instanceof javafx.stage.Stage stage) {
            var title = stage.getTitle();
            if (title == null || title.isEmpty()) {
                title = "LoomMainWindow-" + System.nanoTime();
                stage.setTitle(title);
            }
            parentHandle = NativeWindowUtils.getJavaFXWindowHandle(title);
            if (title.startsWith("LoomMainWindow-")) {
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
        if (bridge != null) {
            bridge.resetZoom();
        }
    }

    private synchronized void startBridgeIfNeeded() {
        if (bridgeStarted) {
            return;
        }

        var scene = getScene();
        if (scene == null) return;
        var window = scene.getWindow();
        if (window == null || !window.isShowing()) return;

        long parentHandle = 0L;
        if (SystemUtils.isWindows() && window instanceof javafx.stage.Stage stage) {
            var title = stage.getTitle();
            if (title == null || title.isEmpty()) {
                title = "LoomMainWindow-" + System.nanoTime();
                stage.setTitle(title);
            }

            parentHandle = NativeWindowUtils.getJavaFXWindowHandle(title);

            if (title.startsWith("LoomMainWindow-")) {
                stage.setTitle("");
            }

            if (parentHandle == 0L) {
                log.warn("HWND not found yet, retrying bridge init...");
                var pause = new javafx.animation.PauseTransition(javafx.util.Duration.millis(50));
                pause.setOnFinished(e -> startBridgeIfNeeded());
                pause.play();
                return;
            }
        }

        bridgeStarted = true;

        int w = 400, h = 300, x = 0, y = 0;
        var screenBounds = getBoundsInScreen();

        if (screenBounds != null) {
            if (SystemUtils.isWindows()) {
                double scaleX = window.getOutputScaleX();
                double scaleY = window.getOutputScaleY();
                x = (int) Math.round((screenBounds.getMinX() - window.getX()) * scaleX);
                y = (int) Math.round((screenBounds.getMinY() - window.getY()) * scaleY);
                w = (int) Math.round(screenBounds.getWidth() * scaleX);
                h = (int) Math.round(screenBounds.getHeight() * scaleY);
            } else {
                x = (int) Math.round(screenBounds.getMinX());
                y = (int) Math.round(screenBounds.getMinY());
                w = (int) Math.round(screenBounds.getWidth());
                h = (int) Math.round(screenBounds.getHeight());
            }
        }

        bridge.init(startUrl, parentHandle, x, y, Math.max(w, 400), Math.max(h, 300));
        bridge.setVisible(true);
        log.info("NativeWebViewBridge started — url={}", startUrl);

        Platform.runLater(this::syncBounds);
    }

    private void syncBounds() {
        if (!bridgeStarted || getScene() == null || getScene().getWindow() == null || bridge == null) {
            return;
        }

        var window = getScene().getWindow();
        var screenBounds = getBoundsInScreen();
        if (screenBounds == null) return;

        int w, h, x, y;

        if (SystemUtils.isWindows()) {
            double scaleX = window.getOutputScaleX();
            double scaleY = window.getOutputScaleY();
            x = (int) Math.round((screenBounds.getMinX() - window.getX()) * scaleX);
            y = (int) Math.round((screenBounds.getMinY() - window.getY()) * scaleY);
            w = (int) Math.round(screenBounds.getWidth() * scaleX);
            h = (int) Math.round(screenBounds.getHeight() * scaleY);
        } else {
            x = (int) Math.round(screenBounds.getMinX());
            y = (int) Math.round(screenBounds.getMinY());
            w = (int) Math.round(screenBounds.getWidth());
            h = (int) Math.round(screenBounds.getHeight());
        }

        bridge.updateBounds(x, y, w, h);
    }

    private Bounds getBoundsInScreen() {
        if (getScene() == null || getScene().getWindow() == null) return null;
        return localToScreen(getBoundsInLocal());
    }

    public void setCurrentConfig(AiConfiguration.AiConfig config) {
        bridge.setCurrentConfig(config);
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
