package to.sparkapp.app.windows;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import lombok.Getter;
import lombok.Setter;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.config.WindowSizePreset.Slot;

/**
 * Coordinates placement, pointer interaction and quick access for the main stage.
 */
public final class WindowController {
    public enum Visibility {
        HIDDEN, OPENING, VISIBLE, CLOSING
    }

    private final Stage stage;
    private final WindowGeometry geometry;
    private final SiteWindowSizes siteSizes;
    private final WindowPointerInteraction pointer;
    private final QuickAccessPill pill;
    private final WindowMotion motion;
    private final ListChangeListener<Screen> screensChanged = change -> updateScreens();
    @Getter
    private Visibility visibility = Visibility.HIDDEN;
    @Setter
    private Runnable onReveal;

    public WindowController(Stage stage, AppPreferences preferences) {
        this.stage = stage;
        geometry = new WindowGeometry(stage, preferences);
        siteSizes = new SiteWindowSizes(stage, preferences, geometry);
        motion = new WindowMotion(stage, preferences, this::isDocked, () -> geometry.monitor.getMinY(),
                state -> visibility = state);
        pointer = new WindowPointerInteraction(stage, geometry, this::settleReveal);
        pill = new QuickAccessPill(stage, preferences, () -> geometry.monitor, this::revealWindow);
        Screen.getScreens().addListener(screensChanged);
        stage.addEventHandler(WindowEvent.WINDOW_SHOWING, event -> motion.prepareOpen());
        stage.showingProperty().addListener((observable, previous, shown) -> updateVisibility(shown));
    }

    private void updateScreens() {
        ensureVisible();
        persist();
        refreshPill();
    }

    private void revealWindow() {
        if (onReveal != null) {
            onReveal.run();
        }
    }

    private void updateVisibility(boolean shown) {
        visibility = shown ? Visibility.VISIBLE : Visibility.HIDDEN;

        if (!shown) {
            settleReveal();
            cancelInteraction();
            refreshPill();

            return;
        }

        pill.windowShown();

        motion.open();
    }

    private void settleReveal() {
        motion.settle();
    }

    public ReadOnlyBooleanProperty dockedProperty() {
        return geometry.docked.getReadOnlyProperty();
    }

    public boolean isDocked() {
        return geometry.isDocked();
    }

    public void opening() {
        if (stage.isShowing()) {
            if (visibility == Visibility.CLOSING) {
                motion.open();
            }
        } else {
            ensureVisible();
            visibility = Visibility.OPENING;
        }
    }

    public void hide(Runnable completed) {
        cancelInteraction();
        persist();
        motion.hide(completed);
    }

    public void refreshMotion() {
        motion.refresh();
    }

    void handleHiddenPointer(double x, double y, PointerApproach approach) {
        pill.handleHiddenPointer(x, y, approach);
    }

    public void refreshPill() {
        pill.refreshPill();
    }

    public void installDragArea(Node area) {
        pointer.installDragArea(area);
    }

    public void installResize(Scene scene, Node heightGrip) {
        pointer.installResize(scene, heightGrip);
    }

    public void dock() {
        settleReveal();
        geometry.dock();
    }

    public void toggleDock() {
        settleReveal();
        geometry.toggleDock();
    }

    public void preset(double width, double height) {
        settleReveal();
        geometry.preset(width, height);
    }

    public void preset(Slot slot) {
        settleReveal();
        geometry.preset(slot);
    }

    /** Gives the window the size of the site that is now on screen. */
    public void showSite(String siteId) {
        settleReveal();
        siteSizes.show(siteId);
    }

    public boolean hasOwnSize(String siteId) {
        return siteSizes.hasOwnSize(siteId);
    }

    /** Lets a site keep the window's current size as its own, or makes it use the shared size again. */
    public void setOwnSize(String siteId, boolean own) {
        settleReveal();
        siteSizes.setOwnSize(siteId, own);
        persist();
    }

    public void fill() {
        settleReveal();
        siteSizes.beforeFill();
        geometry.fill();
    }

    public void restoreManualSize() {
        settleReveal();
        geometry.restoreManualSize();
    }

    public void ensureVisible() {
        settleReveal();
        geometry.ensureVisible();
    }

    public void cancelInteraction() {
        pointer.cancel();
    }

    public void persist() {
        geometry.persist(motion.restingY());
    }

    public void dispose() {
        pill.dispose();
        settleReveal();
        cancelInteraction();
        persist();
        Screen.getScreens().removeListener(screensChanged);
    }
}
