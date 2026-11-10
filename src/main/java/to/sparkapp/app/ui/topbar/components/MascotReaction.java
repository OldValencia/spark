package to.sparkapp.app.ui.topbar.components;

public enum MascotReaction {
    HOVER,
    GREETING,
    SITES,
    SETTINGS,
    RESIZE,
    SITE_SWITCH,
    WAKE,
    /** A site was added, a page came back after trouble, or someone said hello. */
    HAPPY,
    /** A site sent the page to another website, or a file arrived over Spark. */
    SURPRISED,
    /** A site was muted. */
    SHUSH,
    /** Clicked many times in a row. */
    DIZZY,
    /** A site was removed. */
    FAREWELL
}
