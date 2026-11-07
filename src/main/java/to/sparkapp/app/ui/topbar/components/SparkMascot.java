package to.sparkapp.app.ui.topbar.components;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import javafx.util.Duration;
import to.sparkapp.app.config.AppPreferences;

/** Short, local reactions only; no idle loop or page inspection. */
public final class SparkMascot extends Button {
    private final AppPreferences preferences;
    private final Stage owner;
    private final MascotFace face = new MascotFace();
    private final Timeline reaction = new Timeline();
    private final MascotPointer pointer = new MascotPointer(this::lookAtScreen);
    private final EventHandler<MouseEvent> gaze = this::look;
    private final ChangeListener<Boolean> showing = (observable, before, visible) -> windowChanged(visible);
    private final ChangeListener<Boolean> iconified = (observable, before, minimized) -> refresh();
    private final ChangeListener<Scene> sceneChanged = (observable, before, scene) -> attach(before, scene);

    public SparkMascot(Stage owner, AppPreferences preferences) {
        this.owner = owner;
        this.preferences = preferences;
        setGraphic(face);
        setMinSize(30, 30);
        setPrefSize(30, 30);
        setMaxSize(30, 30);
        getStyleClass().add("spark-mascot");
        setAccessibleText("Spark mascot. Say hello");
        setTooltip(ShellTooltip.create("A little spark. Say hello"));
        setOnMouseEntered(event -> react(false));
        setOnMouseExited(event -> face.look(0, 0));
        setOnAction(event -> react(true));
        owner.showingProperty().addListener(showing);
        owner.iconifiedProperty().addListener(iconified);
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

        var position = sceneToLocal(event.getSceneX(), event.getSceneY());
        face.look((position.getX() - 15) / 100, (position.getY() - 15) / 120);
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

        var local = screenToLocal(position);

        if (local != null) {
            face.look((local.getX() - 15) / 100, (local.getY() - 15) / 120);
        }
    }

    private void react(boolean greeting) {
        if (!animated()) {
            return;
        }

        reset();
        var blink = new SimpleDoubleProperty(1);
        blink.addListener((observable, before, value) -> face.blink(value.doubleValue()));
        reaction.getKeyFrames().setAll(
                new KeyFrame(Duration.ZERO, new KeyValue(blink, 1)),
                new KeyFrame(Duration.millis(85), new KeyValue(blink, 0.15),
                        new KeyValue(face.rotateProperty(), greeting ? -7 : 4, Interpolator.EASE_OUT),
                        new KeyValue(face.translateYProperty(), greeting ? -3 : -1, Interpolator.EASE_OUT)),
                new KeyFrame(Duration.millis(180), new KeyValue(blink, 1)),
                new KeyFrame(Duration.millis(360), new KeyValue(face.rotateProperty(), 0, Interpolator.EASE_OUT),
                        new KeyValue(face.translateYProperty(), 0, Interpolator.EASE_OUT)));
        reaction.playFromStart();
    }

    private void reset() {
        reaction.stop();
        face.setRotate(0);
        face.setTranslateY(0);
        face.look(0, 0);
        face.blink(1);
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
            react(true);
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
    }
}
