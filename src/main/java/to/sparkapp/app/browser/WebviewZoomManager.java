package to.sparkapp.app.browser;

import java.util.Locale;
import java.util.function.Consumer;

import javafx.application.Platform;
import lombok.Setter;
import to.sparkapp.app.config.AppPreferences;

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
        if (!appPreferences.isZoomEnabled()) {
            return;
        }

        switch (direction) {
            case "up" -> changeZoom(true);
            case "down" -> changeZoom(false);
            case "reset" -> resetZoom();
        }
    }

    private void changeZoom(boolean increase) {
        var step = 0.5;
        var newLevel = currentZoom + (increase ? step : -step);
        newLevel = Math.clamp(newLevel, -3.0, 4.0);
        setZoomInternal(newLevel);
    }

    public void resetZoom() {
        setZoomInternal(0.0);
    }

    public double getZoomPercent() {
        return Math.round(Math.pow(1.2, appPreferences.isZoomEnabled() ? currentZoom : 0) * 100);
    }

    private void setZoomInternal(double level) {
        this.currentZoom = level;
        appPreferences.setLastZoomValue(level);
        bridge.dispatch(this::applyZoomCss);
        updateZoomDisplay(level);
    }

    public void applyZoomCss() {
        // With zoom switched off the page must render at 100 %, whatever level was
        // stored back when the feature was still enabled.
        var level = appPreferences.isZoomEnabled() ? currentZoom : 0.0;
        var scale = Math.pow(1.2, level);
        var js = String.format(Locale.ROOT, "document.documentElement.style.zoom='%.4f';", scale);
        bridge.eval(js);
    }

    private void updateZoomDisplay(double level) {
        if (zoomCallback != null) {
            var pct = Math.pow(1.2, level) * 100.0;
            var displayVal = (double) Math.round(pct);
            Platform.runLater(() -> zoomCallback.accept(displayVal));
        }
    }
}
