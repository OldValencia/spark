package to.sparkapp.app.config;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Settings that belong to one site. A missing value means the application default.
 *
 * @param zoomLevel zoom step, where the page scale is 1.2 to the power of this level
 */
record SiteSettings(Double zoomLevel) {

    static final SiteSettings DEFAULTS = new SiteSettings(null);

    SiteSettings withZoomLevel(Double level) {
        return new SiteSettings(level);
    }

    @JsonIgnore
    boolean isEmpty() {
        return zoomLevel == null;
    }
}
