package to.sparkapp.app.ui.topbar;

import java.util.function.BooleanSupplier;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.value.ObservableBooleanValue;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Window;
import javafx.util.Duration;
import to.sparkapp.app.ui.topbar.components.ShellIcon;

/**
 * Shared surface for the shell's small action menus.
 */
class ShellPopover extends Popup {
    private static final double OWNER_MARGIN = 8;
    private final VBox body = new VBox(5);
    private final InvalidationListener reposition = observable -> position();
    private Node anchor;
    private Window owner;
    private long dismissal;
    private final PopupMotion motion;

    ShellPopover(String heading, double width) {
        this(heading, width, () -> false);
    }

    ShellPopover(String heading, double width, BooleanSupplier reducedMotion) {
        motion = new PopupMotion(this, reducedMotion);
        setAutoHide(true);
        setHideOnEscape(true);
        body.getStyleClass().add("shell-popup");
        body.setPrefWidth(width);
        body.getStylesheets().add(getClass().getResource("/css/shell.css").toExternalForm());

        var title = new Label(heading);
        title.getStyleClass().add("popover-heading");
        body.getChildren().add(title);
        getContent().add(body);
        setOnShown(event -> observeOwner());
        setOnHidden(event -> releaseOwner());
    }

    private void observeOwner() {
        owner.xProperty().addListener(reposition);
        owner.yProperty().addListener(reposition);
        owner.widthProperty().addListener(reposition);
        owner.heightProperty().addListener(reposition);
    }

    private void releaseOwner() {
        if (owner == null) {
            return;
        }

        owner.xProperty().removeListener(reposition);
        owner.yProperty().removeListener(reposition);
        owner.widthProperty().removeListener(reposition);
        owner.heightProperty().removeListener(reposition);
    }

    void add(Node node) {
        body.getChildren().add(node);
    }

    void action(String icon, String title, Runnable command) {
        var button = new Button(title, ShellIcon.create(icon));
        button.getStyleClass().add("shell-row");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setGraphicTextGap(10);
        button.setOnAction(event -> {
            dismiss();
            command.run();
        });
        add(button);
    }

    Button setting(String icon, String title, ObservableBooleanValue enabled, Runnable change) {
        return setting(icon, title, enabled, change, false);
    }

    Button setting(String icon, String title, ObservableBooleanValue enabled, Runnable change, boolean refreshPeer) {
        var row = new ShellSettingRow(icon, title, enabled, () -> changeSetting(change, refreshPeer));
        add(row);

        return row;
    }

    private void changeSetting(Runnable change, boolean refreshPeer) {
        var version = dismissal;
        change.run();

        Runnable restore = () -> restoreAfterChange(version, refreshPeer);

        if (refreshPeer) {
            // Changing always-on-top recreates the native peer asynchronously.
            var pause = new PauseTransition(Duration.millis(40));
            pause.setOnFinished(event -> restore.run());
            pause.play();
        } else {
            Platform.runLater(restore);
        }
    }

    private void restoreAfterChange(long version, boolean refreshPeer) {
        if (version != dismissal || owner == null || !owner.isShowing()) {
            return;
        }

        if (refreshPeer) {
            hide();
        }

        if (!isShowing()) {
            showAtAnchor();
        }
    }

    void toggle(Node anchor) {
        if (isShowing()) {
            dismiss();

            return;
        }

        this.anchor = anchor;
        showAtAnchor();
    }

    void dismiss() {
        dismissal++;
        hide();
    }

    private void showAtAnchor() {
        var bounds = anchor.localToScreen(anchor.getBoundsInLocal());

        if (bounds != null) {
            owner = anchor.getScene().getWindow();
            show(owner, anchorX(bounds), bounds.getMaxY() + OWNER_MARGIN);
        }
    }

    private double anchorX(Bounds bounds) {
        var rightLimit = owner.getX() + owner.getWidth() - body.getPrefWidth() - OWNER_MARGIN;

        return Math.max(owner.getX() + OWNER_MARGIN, Math.min(bounds.getMinX(), rightLimit));
    }

    private void position() {
        if (!isShowing() || anchor == null) {
            return;
        }

        var bounds = anchor.localToScreen(anchor.getBoundsInLocal());

        if (bounds != null) {
            motion.settle();
            setX(anchorX(bounds));
            setY(bounds.getMaxY() + OWNER_MARGIN);
        }
    }
}
