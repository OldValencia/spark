package to.sparkapp.app.config;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Settings that belong to one site. A missing value means the application default.
 *
 * @param zoomLevel       zoom step, where the page scale is 1.2 to the power of this level
 * @param muted           whether the site is always muted
 * @param mutedWhenHidden whether the site is muted while Spark is hidden
 * @param windowSize      the site's own window size, or {@code null} when it uses the size shared by all sites
 * @param mobile          whether the site shows its mobile version
 */
record SiteSettings(Double zoomLevel, Boolean muted, Boolean mutedWhenHidden, SiteWindowSize windowSize,
                    Boolean mobile) {

    static final SiteSettings DEFAULTS = new SiteSettings(null, null, null, null, null);

    SiteSettings withZoomLevel(Double level) {
        return new SiteSettings(level, muted, mutedWhenHidden, windowSize, mobile);
    }

    SiteSettings withWindowSize(SiteWindowSize size) {
        return new SiteSettings(zoomLevel, muted, mutedWhenHidden, size, mobile);
    }

    SiteSettings withMobile(Boolean value) {
        return new SiteSettings(zoomLevel, muted, mutedWhenHidden, windowSize, value);
    }

    Boolean mute(MuteRule rule) {
        return switch (rule) {
            case ALWAYS -> muted;
            case WHILE_HIDDEN -> mutedWhenHidden;
        };
    }

    SiteSettings withMute(MuteRule rule, Boolean value) {
        return switch (rule) {
            case ALWAYS -> new SiteSettings(zoomLevel, value, mutedWhenHidden, windowSize, mobile);
            case WHILE_HIDDEN -> new SiteSettings(zoomLevel, muted, value, windowSize, mobile);
        };
    }

    @JsonIgnore
    boolean isEmpty() {
        return zoomLevel == null && muted == null && mutedWhenHidden == null && windowSize == null && mobile == null;
    }
}
