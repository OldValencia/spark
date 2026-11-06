package to.sparkapp.app.ui.webview;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.PauseTransition;
import javafx.animation.RotateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.StrokeLineCap;
import javafx.util.Duration;
import to.sparkapp.app.ui.Theme;

/**
 * Centered loading indicator with explicit recovery actions for failed navigation.
 */
class WebViewLoadingOverlay extends StackPane {
    private final StackPane indicator = new StackPane();
    private final ImageView iconView = new ImageView();
    private final RotateTransition rotation;
    private final Label message = new Label();
    private final PageRecoveryActions actions = new PageRecoveryActions();
    private PauseTransition activeTimer;

    WebViewLoadingOverlay() {
        setAlignment(Pos.CENTER);
        setPadding(new Insets(24));
        setStyle("-fx-background-color: " + Theme.toHex(Theme.BG_DEEP) + ";");
        setVisible(false);

        var track = new Circle(17, Color.TRANSPARENT);
        track.setStroke(Color.web("#ffffff", 0.08));
        track.setStrokeWidth(2.5);

        var arc = new Arc(0, 0, 17, 17, 35, 95);
        arc.setType(ArcType.OPEN);
        arc.setFill(Color.TRANSPARENT);
        arc.setStroke(Color.web("#BFD6AB"));
        arc.setStrokeWidth(2.5);
        arc.setStrokeLineCap(StrokeLineCap.ROUND);
        // Fix the rotation origin at the circle center, not the arc's partial bounds.

        var ring = new Group(track, arc);
        indicator.setMinSize(44, 44);
        indicator.setPrefSize(44, 44);
        indicator.setMaxSize(44, 44);
        iconView.setFitWidth(16);
        iconView.setFitHeight(16);
        iconView.setPreserveRatio(true);
        indicator.getChildren().addAll(ring, iconView);
        rotation = new RotateTransition(Duration.millis(1100), ring);
        rotation.setByAngle(360);
        rotation.setInterpolator(Interpolator.LINEAR);
        rotation.setCycleCount(Animation.INDEFINITE);

        message.setWrapText(true);
        message.setAlignment(Pos.CENTER);
        message.setMaxWidth(360);
        message.setStyle("-fx-text-fill: #b9beb5; -fx-text-alignment: center; -fx-font-size: 13px;");
        for (var node : new Region[]{indicator, message, actions}) {
            node.managedProperty().bind(node.visibleProperty());
        }

        var content = new VBox(18, indicator, message, actions);
        content.setAlignment(Pos.CENTER);
        content.setMaxWidth(380);
        content.setMaxHeight(Region.USE_PREF_SIZE);
        getChildren().add(content);
        visibleProperty().addListener((observable, previous, visible) -> {
            if (!visible) {
                pauseAnimation();
            }
        });
    }

    void activate(Image icon, long autoHideMs, Runnable onAutoHide) {
        cancelTimer();
        iconView.setImage(icon);
        indicator.setVisible(true);
        message.setVisible(false);
        actions.setVisible(false);
        setAccessibleText("Loading site");
        toFront();
        setVisible(true);

        if (rotation.getStatus() != Animation.Status.RUNNING) {
            rotation.playFromStart();
        }

        if (autoHideMs > 0) {
            activeTimer = new PauseTransition(Duration.millis(autoHideMs));
            activeTimer.setOnFinished(event -> {
                deactivate();

                if (onAutoHide != null) {
                    onAutoHide.run();
                }
            });
            activeTimer.play();
        }
    }

    void pauseAnimation() {
        rotation.stop();
    }

    void deactivate() {
        cancelTimer();
        pauseAnimation();
        setVisible(false);
    }

    boolean isActive() {
        return isVisible();
    }

    void showError(String text, Runnable onRetry, Runnable onShowPage, Runnable onEditSite) {
        cancelTimer();
        pauseAnimation();
        indicator.setVisible(false);
        message.setText(text);
        message.setVisible(true);
        actions.configure(onRetry, onEditSite, onShowPage);
        actions.setVisible(true);
        setAccessibleText(text);
        toFront();
        setVisible(true);
    }

    private void cancelTimer() {
        if (activeTimer != null) {
            activeTimer.stop();
            activeTimer = null;
        }
    }
}
