package to.sparkapp.app.config;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Settings that belong to one site. A missing value means the application default.
 *
 * @param zoomLevel       zoom step, where the page scale is 1.2 to the power of this level
 * @param muted           whether the site is always muted
 * @param mutedWhenHidden whether the site is muted while Spark is hidden
 */
record SiteSettings(Double zoomLevel, Boolean muted, Boolean mutedWhenHidden) {

    static final SiteSettings DEFAULTS = new SiteSettings(null, null, null);

    SiteSettings withZoomLevel(Double level) {
        return new SiteSettings(level, muted, mutedWhenHidden);
    }

    Boolean mute(MuteRule rule) {
        return switch (rule) {
            case ALWAYS -> muted;
            case WHILE_HIDDEN -> mutedWhenHidden;
        };
    }

    SiteSettings withMute(MuteRule rule, Boolean value) {
        return switch (rule) {
            case ALWAYS -> new SiteSettings(zoomLevel, value, mutedWhenHidden);
            case WHILE_HIDDEN -> new SiteSettings(zoomLevel, muted, value);
        };
    }

    @JsonIgnore
    boolean isEmpty() {
        return zoomLevel == null && muted == null && mutedWhenHidden == null;
    }
}
