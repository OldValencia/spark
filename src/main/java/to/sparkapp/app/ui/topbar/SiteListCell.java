package to.sparkapp.app.ui.topbar;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.css.PseudoClass;
import javafx.scene.control.ListCell;
import javafx.util.Duration;
import lombok.RequiredArgsConstructor;
import to.sparkapp.app.config.AiConfiguration.AiConfig;

@RequiredArgsConstructor
final class SiteListCell extends ListCell<AiConfig> {
    private final SiteListContext context;
    private final SiteListDrag drag;

    final PseudoClass before = PseudoClass.getPseudoClass("drop-before");
    final PseudoClass after = PseudoClass.getPseudoClass("drop-after");
    private Timeline movement;
    private double targetOffset;

    void animateTo(double offset) {
        if (targetOffset == offset) {
            return;
        }

        targetOffset = offset;

        if (movement != null) {
            movement.stop();
        }

        if (context.preferences().isReducedMotion()) {
            setTranslateY(offset);

            return;
        }

        movement = new Timeline(
                new KeyFrame(Duration.millis(150), new KeyValue(translateYProperty(), offset, Interpolator.EASE_BOTH)));
        movement.play();
    }

    void resetPosition() {
        if (movement != null) {
            movement.stop();
        }

        targetOffset = 0;
        setTranslateY(0);
        setOpacity(1);
        clearMarker();
    }

    private void clearMarker() {
        pseudoClassStateChanged(before, false);
        pseudoClassStateChanged(after, false);
    }

    @Override
    protected void updateItem(AiConfig site, boolean empty) {
        super.updateItem(site, empty);
        resetPosition();

        if (empty || site == null) {
            setGraphic(null);
            setText(null);

            return;
        }

        setGraphic(new SiteListRow(context, drag, this, site));
    }
}
