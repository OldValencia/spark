package to.sparkapp.app.windows;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;
import lombok.Getter;
import lombok.Setter;
import to.sparkapp.app.config.AppPreferences;

/**
 * Coordinates placement, pointer interaction and quick access for the main stage.
 */
public final class WindowController {
    public enum Visibility {
        HIDDEN, OPENING, VISIBLE, CLOSING
    }

    private final Stage stage;
    private final AppPreferences preferences;
    private final WindowGeometry geometry;
    private final WindowPointerInteraction pointer;
    private final QuickAccessPill pill;
    private final Timeline reveal = new Timeline();
    private final SimpleDoubleProperty revealY = new SimpleDoubleProperty();
    private final ListChangeListener<Screen> screensChanged = change -> updateScreens();
    private double settledY;
    @Getter
    private Visibility visibility = Visibility.HIDDEN;
    @Setter
    private Runnable onReveal;

    public WindowController(Stage stage, AppPreferences preferences) {
        this.stage = stage;
        this.preferences = preferences;
        geometry = new WindowGeometry(stage, preferences);
        pointer = new WindowPointerInteraction(stage, geometry, this::settleReveal);
        pill = new QuickAccessPill(stage, preferences, () -> geometry.monitor, this::revealWindow);
        revealY.addListener((observable, previous, y) -> stage.setY(y.doubleValue()));
        Screen.getScreens().addListener(screensChanged);
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
            cancelInteraction();
            refreshPill();

            return;
        }

        pill.windowShown();

        if (!preferences.isReducedMotion() && isDocked()) {
            settledY = stage.getY();
            revealY.set(settledY - 12);
            reveal.getKeyFrames()
                    .setAll(new KeyFrame(Duration.millis(120), new KeyValue(revealY, settledY, Interpolator.EASE_OUT)));
            visibility = Visibility.OPENING;
            reveal.setOnFinished(event -> visibility = Visibility.VISIBLE);
            reveal.playFromStart();
        }
    }

    private void settleReveal() {
        if (reveal.getStatus() == Timeline.Status.RUNNING) {
            reveal.stop();
            stage.setY(settledY);
        }
    }

    public ReadOnlyBooleanProperty dockedProperty() {
        return geometry.docked.getReadOnlyProperty();
    }

    public boolean isDocked() {
        return geometry.isDocked();
    }

    public void opening() {
        ensureVisible();
        visibility = stage.isShowing() ? Visibility.VISIBLE : Visibility.OPENING;
    }

    public void closing() {
        visibility = Visibility.CLOSING;
        settleReveal();
        cancelInteraction();
        persist();
    }

    public void handleHiddenPointer(double x, double y) {
        pill.handleHiddenPointer(x, y);
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

    public void fill() {
        settleReveal();
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
        geometry.persist();
    }

    public void dispose() {
        pill.dispose();
        settleReveal();
        cancelInteraction();
        persist();
        Screen.getScreens().removeListener(screensChanged);
    }
}
