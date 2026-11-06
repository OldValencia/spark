package to.sparkapp.app.windows;

import java.util.UUID;
import java.util.function.Supplier;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import lombok.RequiredArgsConstructor;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.utils.NativeWindowUtils;

@RequiredArgsConstructor
final class QuickAccessPillWindow {
    private final AppPreferences preferences;
    private final Supplier<Rectangle2D> monitor;
    private final Runnable reveal;
    private final Runnable cancelDismissal;
    private final Runnable scheduleDismissal;
    private final Timeline pillAnimation = new Timeline();
    private Stage pill;
    private boolean pillPreview;
    private boolean pillHiding;

    boolean isShowing() {
        return pill != null && pill.isShowing();
    }

    boolean canDismiss() {
        return pillPreview && isShowing() && !pillHiding;
    }

    void close() {
        hideImmediately();

        if (pill != null) {
            pill.close();
        }
    }

    void show(boolean preview) {
        if (pill == null) {
            pill = new Stage(StageStyle.TRANSPARENT);
            pill.setAlwaysOnTop(true);
            pill.setTitle("Spark quick access " + UUID.randomUUID());

            var button = new Button("Spark");
            button.setAccessibleText("Open Spark");
            button.setStyle("-fx-background-color:#25262a;-fx-text-fill:#dedee5;-fx-background-radius:0 0" +
                    " 10 10;-fx-padding:4 22;-fx-font-size:11;-fx-cursor:hand;");
            button.setOnAction(e -> {
                hideImmediately();
                reveal.run();
            });
            button.setOnMouseEntered(e -> {
                cancelDismissal.run();

                if (pillHiding) {
                    show(true);
                }
            });
            button.setOnMouseExited(e -> {
                if (!preferences.isTopPill()) {
                    scheduleDismissal.run();
                }
            });
            pill.setScene(new Scene(button, 100, 26, Color.TRANSPARENT));
        }

        pill.setX(monitor.get().getMinX() + (monitor.get().getWidth() - 100) / 2);
        pill.setY(monitor.get().getMinY());

        boolean animate = !pill.isShowing() || pillHiding;
        pillPreview = preview;
        pillHiding = false;

        var root = pill.getScene().getRoot();
        long foreground = NativeWindowUtils.foregroundWindow();

        if (!pill.isShowing()) {
            root.setTranslateY(preview ? -26 : 0);
            root.setOpacity(preview ? 0 : 1);
            pill.show();
            NativeWindowUtils.makeNonActivatingToolWindow(pill.getTitle());
            NativeWindowUtils.restoreForeground(foreground);
        }

        if (!preview) {
            pillAnimation.stop();
            root.setTranslateY(0);
            root.setOpacity(1);
        } else if (animate) {
            animate(true);
        }
    }

    void animate(boolean show) {
        pillAnimation.stop();
        pillHiding = !show;

        var root = pill.getScene().getRoot();
        double offset = show ? 0 : -26;
        double opacity = show ? 1 : 0;

        if (preferences.isReducedMotion()) {
            root.setTranslateY(offset);
            root.setOpacity(opacity);

            if (!show) {
                pill.hide();
                pillPreview = false;
                pillHiding = false;
            }

            return;
        }

        pillAnimation.getKeyFrames().setAll(new KeyFrame(Duration.millis(150),
                new KeyValue(root.translateYProperty(), offset, Interpolator.EASE_OUT),
                new KeyValue(root.opacityProperty(), opacity, Interpolator.EASE_OUT)));
        pillAnimation.setOnFinished(e -> {
            if (!show) {
                pill.hide();
                pillPreview = false;
                pillHiding = false;
            }
        });
        pillAnimation.playFromStart();
    }

    void hideImmediately() {
        cancelDismissal.run();
        pillAnimation.stop();
        pillPreview = false;
        pillHiding = false;

        if (pill != null) {
            pill.hide();
        }
    }
}
