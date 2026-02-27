package to.sparkapp.app.ui.webview;

import javafx.animation.Animation;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.geometry.Pos;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import to.sparkapp.app.ui.Theme;

/**
 * A fullscreen loading overlay that sits in the WebView pane while the browser
 * navigates to a new page. Shows a pulsing provider icon and blocks input.
 */
class WebViewLoadingOverlay extends VBox {

    private final ImageView iconView;
    private final ScaleTransition pulse;
    private PauseTransition activeTimer;

    WebViewLoadingOverlay() {
        setAlignment(Pos.CENTER);
        setStyle("-fx-background-color: " + Theme.toHex(Theme.BG_DEEP) + ";");
        setVisible(false);
        setMouseTransparent(false);

        iconView = new ImageView();
        iconView.setFitWidth(32);
        iconView.setFitHeight(32);
        iconView.setPreserveRatio(true);
        iconView.setSmooth(true);

        var glow = new DropShadow();
        glow.setColor(Color.web("#FFFFFF66"));
        glow.setRadius(15);
        iconView.setEffect(glow);

        getChildren().add(iconView);

        pulse = new ScaleTransition(Duration.millis(600), iconView);
        pulse.setFromX(0.9);
        pulse.setFromY(0.9);
        pulse.setToX(1.2);
        pulse.setToY(1.2);
        pulse.setAutoReverse(true);
        pulse.setCycleCount(Animation.INDEFINITE);
    }

    /**
     * Activates the overlay with the given icon, hiding after {@code autoHideMs} ms.
     * Pass {@code autoHideMs <= 0} to disable auto-hide.
     */
    void activate(Image icon, long autoHideMs, Runnable onAutoHide) {
        cancelTimer();

        iconView.setImage(icon);
        toFront();
        setVisible(true);
        pulse.play();

        if (autoHideMs > 0) {
            activeTimer = new PauseTransition(Duration.millis(autoHideMs));
            activeTimer.setOnFinished(e -> {
                deactivate();
                if (onAutoHide != null) onAutoHide.run();
            });
            activeTimer.play();
        }
    }

    /** Immediately hides the overlay and stops any pending auto-hide timer. */
    void deactivate() {
        cancelTimer();
        pulse.stop();
        setVisible(false);
    }

    boolean isActive() {
        return isVisible();
    }

    private void cancelTimer() {
        if (activeTimer != null) {
            activeTimer.stop();
            activeTimer = null;
        }
    }
}