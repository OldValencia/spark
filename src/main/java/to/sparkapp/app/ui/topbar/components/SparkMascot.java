package to.sparkapp.app.ui.topbar.components;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableBooleanValue;
import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import to.sparkapp.app.config.AppPreferences;

/**
 * Short, local reactions plus a lasting mood from the page state and activity in Spark. The page
 * content itself is never inspected.
 */
public final class SparkMascot extends Button {
    private static final long AWAY_NANOS = 60_000_000_000L;

    private final AppPreferences preferences;
    private final Stage owner;
    private final MascotFace face = new MascotFace();
    private final MascotExpressions expressions = new MascotExpressions(face, this::animated);
    private final MascotPointer pointer = new MascotPointer(this::lookAtScreen);
    private final EventHandler<MouseEvent> gaze = this::look;
    private final EventHandler<KeyEvent> typing = event -> expressions.activity();
    private final ChangeListener<Boolean> showing = (observable, before, visible) -> windowChanged(visible);
    private final ChangeListener<Boolean> iconified = (observable, before, minimized) -> refresh();
    private final ChangeListener<Number> resized = (observable, before, size) -> react(MascotReaction.RESIZE);
    private final ChangeListener<Scene> sceneChanged = (observable, before, scene) -> attach(before, scene);
    private long hiddenAt;

    public SparkMascot(Stage owner, AppPreferences preferences) {
        this.owner = owner;
        this.preferences = preferences;
        setGraphic(face);
        setMinSize(40, 44);
        setPrefSize(40, 44);
        setMaxSize(40, 44);
        getStyleClass().add("spark-mascot");
        setAccessibleText("Sparky. Say hello");
        setTooltip(ShellTooltip.create("A little spark. Say hello"));
        setOnMouseEntered(event -> react(MascotReaction.HOVER));
        setOnMouseExited(event -> face.look(0, 0));
        setOnAction(event -> react(MascotReaction.GREETING));
        owner.showingProperty().addListener(showing);
        owner.iconifiedProperty().addListener(iconified);
        owner.widthProperty().addListener(resized);
        owner.heightProperty().addListener(resized);
        sceneProperty().addListener(sceneChanged);
        refresh();
    }

    private boolean animated() {
        return owner.isShowing() && !owner.isIconified() && preferences.isMascotEnabled() &&
                !preferences.isReducedMotion();
    }

    /** Lets the mascot think while the page loads and look sad when it fails. */
    public void observePage(ObservableBooleanValue loading, ObservableBooleanValue failed) {
        expressions.observePage(loading, failed);
    }

    private void look(MouseEvent event) {
        if (!animated()) {
            return;
        }

        expressions.activity();
        lookAtLocal(sceneToLocal(event.getSceneX(), event.getSceneY()));
    }

    private void lookAtScreen(Point2D position) {
        if (!animated() || getScene() == null) {
            return;
        }

        var root = getScene().getRoot();
        var inside = root.screenToLocal(position);

        if (inside == null || !root.getLayoutBounds().contains(inside)) {
            face.look(0, 0);

            return;
        }

        expressions.activity();
        lookAtLocal(screenToLocal(position));
    }

    private void lookAtLocal(Point2D position) {
        if (position != null) {
            face.look((position.getX() - getWidth() / 2) / 100,
                    (position.getY() - getHeight() / 2) / 120);
        }
    }

    public void react(MascotReaction expression) {
        expressions.react(expression);
    }

    /** Whether the mascot can leave the bar to guide the tour. */
    public boolean canGuide() {
        return preferences.isMascotEnabled() && isVisible() && getScene() != null;
    }

    /** The mascot's top-left corner on screen, where a guide steps out and returns. */
    public Point2D screenPosition() {
        return localToScreen(0, 0);
    }

    /** Hides the mascot in the bar while its guide is away showing the tour. */
    public void setAway(boolean away) {
        expressions.reset();
        setOpacity(away ? 0 : 1);
        setMouseTransparent(away || !isVisible());
    }

    private void attach(Scene before, Scene scene) {
        if (before != null) {
            before.removeEventFilter(MouseEvent.MOUSE_MOVED, gaze);
            before.removeEventFilter(KeyEvent.KEY_PRESSED, typing);
        }

        if (scene != null) {
            scene.addEventFilter(MouseEvent.MOUSE_MOVED, gaze);
            scene.addEventFilter(KeyEvent.KEY_PRESSED, typing);
        }
    }

    /** Opening Spark after a while, or while the mascot sleeps, wakes it instead of a greeting. */
    private void windowChanged(boolean visible) {
        expressions.reset();
        pointer.setEnabled(animated());

        if (!visible) {
            hiddenAt = System.nanoTime();
            expressions.setActive(false);

            return;
        }

        var waking = expressions.isAsleep() || System.nanoTime() - hiddenAt >= AWAY_NANOS;
        expressions.setActive(animated());
        react(waking ? MascotReaction.WAKE : MascotReaction.GREETING);
    }

    public void refresh() {
        expressions.reset();
        // A hidden mascot gives its place to the site tabs.
        setVisible(preferences.isMascotEnabled());
        setManaged(isVisible());
        setMouseTransparent(!isVisible());
        pointer.setEnabled(animated());
        expressions.setActive(animated());
    }

    public void dispose() {
        expressions.dispose();
        pointer.dispose();
        attach(getScene(), null);
        sceneProperty().removeListener(sceneChanged);
        owner.showingProperty().removeListener(showing);
        owner.iconifiedProperty().removeListener(iconified);
        owner.widthProperty().removeListener(resized);
        owner.heightProperty().removeListener(resized);
    }
}
