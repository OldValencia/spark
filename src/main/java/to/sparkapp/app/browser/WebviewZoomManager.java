package to.sparkapp.app.browser;

import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

import javafx.application.Platform;
import lombok.Setter;
import to.sparkapp.app.config.AppPreferences;

/**
 * Keeps the zoom of one site: reads and stores it by site id, and reports it to the zoom controls.
 */
class WebviewZoomManager {

    private final AppPreferences appPreferences;
    private final DoubleConsumer renderer;

    @Setter
    private Consumer<Double> zoomCallback;
    private volatile String siteId;
    private volatile double level = ZoomSteps.DEFAULT_LEVEL;

    /**
     * @param renderer applies a page scale factor, such as 1.2 for 120 %
     */
    WebviewZoomManager(AppPreferences appPreferences, DoubleConsumer renderer) {
        this.appPreferences = appPreferences;
        this.renderer = renderer;
    }

    /** Switches to the stored zoom of the site this browser shows. */
    void showSite(String siteId) {
        this.siteId = siteId;
        refresh();
    }

    /** Reloads the stored zoom, for example after zoom was switched off or on in the settings. */
    void refresh() {
        var site = siteId;
        level = site == null ? ZoomSteps.DEFAULT_LEVEL : ZoomSteps.clamp(appPreferences.getSiteZoomLevel(site));
        apply();
        updateZoomDisplay();
    }

    void handleZoomCommand(String direction) {
        if (!appPreferences.isZoomEnabled()) {
            return;
        }

        switch (direction) {
            case "up" -> setLevel(ZoomSteps.next(level, true));
            case "down" -> setLevel(ZoomSteps.next(level, false));
            case "reset" -> resetZoom();
            default -> {
            }
        }
    }

    void resetZoom() {
        setLevel(ZoomSteps.DEFAULT_LEVEL);
    }

    double getZoomPercent() {
        return ZoomSteps.percent(effectiveLevel());
    }

    double currentFactor() {
        return ZoomSteps.factor(effectiveLevel());
    }

    void apply() {
        renderer.accept(currentFactor());
    }

    private void setLevel(double newLevel) {
        level = newLevel;
        var site = siteId;

        if (site != null) {
            appPreferences.setSiteZoomLevel(site, newLevel);
        }

        apply();
        updateZoomDisplay();
    }

    /** With zoom switched off the page renders at 100 %, whatever level is stored. */
    private double effectiveLevel() {
        return appPreferences.isZoomEnabled() ? level : ZoomSteps.DEFAULT_LEVEL;
    }

    private void updateZoomDisplay() {
        var callback = zoomCallback;

        if (callback != null) {
            var percent = getZoomPercent();
            Platform.runLater(() -> callback.accept(percent));
        }
    }
}
