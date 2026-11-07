package to.sparkapp.app.ui.topbar.components;

import javafx.beans.value.ChangeListener;
import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import to.sparkapp.app.config.AppPreferences;

/** Short, local reactions only; no idle loop or page inspection. */
public final class SparkMascot extends Button {
    private final AppPreferences preferences;
    private final Stage owner;
    private final MascotFace face = new MascotFace();
    private final MascotMotion motion = new MascotMotion(face);
    private final MascotPointer pointer = new MascotPointer(this::lookAtScreen);
    private final EventHandler<MouseEvent> gaze = this::look;
    private final ChangeListener<Boolean> showing = (observable, before, visible) -> windowChanged(visible);
    private final ChangeListener<Boolean> iconified = (observable, before, minimized) -> refresh();
    private final ChangeListener<Number> resized = (observable, before, size) -> react(MascotReaction.RESIZE);
    private final ChangeListener<Scene> sceneChanged = (observable, before, scene) -> attach(before, scene);

    public SparkMascot(Stage owner, AppPreferences preferences) {
        this.owner = owner;
        this.preferences = preferences;
        setGraphic(face);
        setMinSize(40, 44);
        setPrefSize(40, 44);
        setMaxSize(40, 44);
        getStyleClass().add("spark-mascot");
        setAccessibleText("Spark mascot. Say hello");
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

    private void look(MouseEvent event) {
        if (!animated()) {
            return;
        }

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

        lookAtLocal(screenToLocal(position));
    }

    private void lookAtLocal(Point2D position) {
        if (position != null) {
            face.look((position.getX() - getWidth() / 2) / 100,
                    (position.getY() - getHeight() / 2) / 120);
        }
    }

    public void react(MascotReaction expression) {
        if (!animated()) {
            return;
        }

        motion.play(expression);
    }

    private void reset() {
        motion.reset();
    }

    private void attach(Scene before, Scene scene) {
        if (before != null) {
            before.removeEventFilter(MouseEvent.MOUSE_MOVED, gaze);
        }

        if (scene != null) {
            scene.addEventFilter(MouseEvent.MOUSE_MOVED, gaze);
        }
    }

    private void windowChanged(boolean visible) {
        reset();
        pointer.setEnabled(animated());

        if (visible) {
            react(MascotReaction.GREETING);
        }
    }

    public void refresh() {
        reset();
        setVisible(preferences.isMascotEnabled());
        setMouseTransparent(!isVisible());
        pointer.setEnabled(animated());
    }

    public void dispose() {
        reset();
        pointer.dispose();
        attach(getScene(), null);
        sceneProperty().removeListener(sceneChanged);
        owner.showingProperty().removeListener(showing);
        owner.iconifiedProperty().removeListener(iconified);
        owner.widthProperty().removeListener(resized);
        owner.heightProperty().removeListener(resized);
    }
}
