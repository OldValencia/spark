package to.sparkapp.app.config;

/** When a site's sound is muted. Each rule has a default for all sites and an optional value per site. */
public enum MuteRule {
    /** The site is always muted. */
    ALWAYS,
    /** The site is muted while Spark is hidden. */
    WHILE_HIDDEN
}
