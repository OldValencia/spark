package to.sparkapp.app.windows;

import javafx.geometry.Point2D;
import javafx.stage.Stage;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.config.SiteWindowSize;

/**
 * Gives a site that keeps its own window size that size whenever it is shown. While such a site is on screen, the
 * size the user chooses becomes its size; otherwise it becomes the size shared by all other sites.
 */
final class SiteWindowSizes {
    private final Stage stage;
    private final AppPreferences preferences;
    private final WindowGeometry geometry;
    /** Where the window stood before a site filled the screen, to return there for the next site. */
    private Point2D positionBeforeFill;
    private String siteId;

    SiteWindowSizes(Stage stage, AppPreferences preferences, WindowGeometry geometry) {
        this.stage = stage;
        this.preferences = preferences;
        this.geometry = geometry;
        geometry.setOnUserResized(this::userResized);
    }

    /** Gives the window the size of the site that is now on screen. */
    void show(String siteId) {
        this.siteId = siteId;
        var size = ownSize(siteId);

        if (size != null && size.fill()) {
            fillScreen();
        } else if (size != null && size.preset() != null) {
            var preset = preferences.getWindowSizePreset(size.preset());
            resize(preset.width(), preset.height());
        } else if (size != null) {
            resize(size.width(), size.height());
        } else {
            resize(geometry.sharedWidth(), geometry.sharedHeight());
        }
    }

    boolean hasOwnSize(String siteId) {
        return ownSize(siteId) != null;
    }

    /** Lets a site keep the window's current size as its own, or makes it use the shared size again. */
    void setOwnSize(String siteId, boolean own) {
        var current = SiteWindowSize.custom(stage.getWidth(), stage.getHeight());
        preferences.setSiteWindowSize(siteId, own ? current : null);

        if (siteId.equals(this.siteId)) {
            show(siteId);
        }
    }

    private SiteWindowSize ownSize(String siteId) {
        return siteId == null ? null : preferences.getSiteWindowSize(siteId);
    }

    /** Remembers where the window stands before the user makes it fill the screen. */
    void beforeFill() {
        if (!geometry.fillsScreen()) {
            positionBeforeFill = new Point2D(stage.getX(), stage.getY());
        }
    }

    private void userResized(SiteWindowSize size) {
        if (!size.fill()) {
            positionBeforeFill = null;
        }

        if (hasOwnSize(siteId)) {
            preferences.setSiteWindowSize(siteId, size);
        } else {
            geometry.rememberSharedSize();
        }
    }

    private void fillScreen() {
        beforeFill();
        geometry.fillScreen();
    }

    private void resize(double width, double height) {
        var corner = geometry.fillsScreen() && positionBeforeFill != null
                ? positionBeforeFill
                : new Point2D(stage.getX(), stage.getY());
        positionBeforeFill = null;
        geometry.resizeFromCorner(corner.getX(), corner.getY(), width, height);
    }
}
