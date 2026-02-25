package to.sparkapp.app.browser;

import javafx.application.Platform;
import lombok.Setter;
import to.sparkapp.app.config.AppPreferences;

import java.util.function.Consumer;

/**
 * Manages browser zoom levels, CSS injection, and zoom UI callbacks.
 */
class WebviewZoomManager {

    private final AppPreferences appPreferences;
    private final WebviewManager bridge;

    @Setter
    private Consumer<Double> zoomCallback;
    private volatile double currentZoom;

    WebviewZoomManager(AppPreferences appPreferences, WebviewManager bridge) {
        this.appPreferences = appPreferences;
        this.bridge = bridge;
        this.currentZoom = appPreferences.getLastZoomValue();
    }

    void handleZoomCommand(String direction) {
        if (!appPreferences.isZoomEnabled()) return;
        switch (direction) {
            case "up" -> changeZoom(true);
            case "down" -> changeZoom(false);
            case "reset" -> resetZoom();
        }
    }

    private void changeZoom(boolean increase) {
        var step = 0.5;
        var newLevel = currentZoom + (increase ? step : -step);
        newLevel = Math.max(-3.0, Math.min(4.0, newLevel));
        setZoomInternal(newLevel);
    }

    public void resetZoom() {
        setZoomInternal(0.0);
    }

    private void setZoomInternal(double level) {
        this.currentZoom = level;
        appPreferences.setLastZoomValue(level);
        bridge.dispatch(this::applyZoomCss);
        updateZoomDisplay(level);
    }

    public void applyZoomCss() {
        var scale = Math.pow(1.2, currentZoom);
        var js = String.format("document.documentElement.style.zoom='%.4f';", scale);
        bridge.eval(js);
    }

    private void updateZoomDisplay(double level) {
        if (zoomCallback != null) {
            var pct = Math.pow(1.2, level) * 100.0;
            var displayVal = Math.round(pct / 5.0) * 5.0;
            Platform.runLater(() -> zoomCallback.accept(displayVal));
        }
    }
}
