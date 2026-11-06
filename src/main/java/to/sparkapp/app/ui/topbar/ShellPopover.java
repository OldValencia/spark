package to.sparkapp.app.ui.topbar;

import javafx.scene.Node;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.Priority;
import javafx.geometry.Pos;
import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.binding.Bindings;
import javafx.css.PseudoClass;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import to.sparkapp.app.ui.topbar.components.ShellIcon;
import javafx.stage.Popup;
import javafx.stage.Window;
import javafx.beans.InvalidationListener;

/** Shared surface for the shell's small action menus. */
final class ShellPopover extends Popup {
    private final VBox body = new VBox(5);
    private Node anchor;
    private Window owner;
    private final InvalidationListener reposition = o -> position();
    private long dismissal;

    ShellPopover(String heading, double width) {
        setAutoHide(true); setHideOnEscape(true);
        body.getStyleClass().add("shell-popup"); body.setPrefWidth(width);
        body.getStylesheets().add(getClass().getResource("/css/shell.css").toExternalForm());
        var title = new Label(heading); title.getStyleClass().add("popover-heading");
        body.getChildren().add(title); getContent().add(body);
        setOnShown(e -> {
            owner.xProperty().addListener(reposition); owner.yProperty().addListener(reposition);
            owner.widthProperty().addListener(reposition); owner.heightProperty().addListener(reposition);
        });
        setOnHidden(e -> {
            if (owner == null) return;
            owner.xProperty().removeListener(reposition); owner.yProperty().removeListener(reposition);
            owner.widthProperty().removeListener(reposition); owner.heightProperty().removeListener(reposition);
        });
    }

    void add(Node node) { body.getChildren().add(node); }

    Button action(String icon, String title, Runnable command) {
        var button = new Button(title, ShellIcon.create(icon)); button.getStyleClass().add("shell-row"); button.setMaxWidth(Double.MAX_VALUE);
        button.setGraphicTextGap(10);
        button.setOnAction(e -> { dismiss(); command.run(); }); add(button);
        return button;
    }

    Button setting(String icon, String title, ObservableBooleanValue enabled, Runnable change) {
        return setting(icon, title, enabled, change, false);
    }

    Button setting(String icon, String title, ObservableBooleanValue enabled, Runnable change, boolean refreshPeer) {
        var label = new Label(title);
        var space = new Region(); HBox.setHgrow(space, Priority.ALWAYS);
        var status = new Label(); status.getStyleClass().add("menu-status");
        status.textProperty().bind(Bindings.when(enabled).then("On").otherwise("Off"));
        var active = PseudoClass.getPseudoClass("on");
        status.pseudoClassStateChanged(active, enabled.get());
        enabled.addListener((o, before, value) -> status.pseudoClassStateChanged(active, value));
        var content = new HBox(10, ShellIcon.create(icon), label, space, status); content.setAlignment(Pos.CENTER_LEFT);
        var row = new Button(); row.setGraphic(content); row.getStyleClass().add("shell-row");
        row.setMaxWidth(Double.MAX_VALUE); content.prefWidthProperty().bind(row.widthProperty().subtract(14));
        row.accessibleTextProperty().bind(Bindings.concat(title, ": ", status.textProperty()));
        row.setOnAction(e -> {
            long version = dismissal;
            change.run();
            Runnable restore = () -> {
                if (version != dismissal || owner == null || !owner.isShowing()) return;
                if (refreshPeer) { hide(); showAtAnchor(); }
                else if (!isShowing()) showAtAnchor();
            };
            if (refreshPeer) {
                var pause = new PauseTransition(Duration.millis(40)); pause.setOnFinished(event -> restore.run()); pause.play();
            } else Platform.runLater(restore);
        }); add(row);
        return row;
    }

    void toggle(Node anchor) {
        if (isShowing()) { dismiss(); return; }
        this.anchor = anchor;
        showAtAnchor();
    }

    void dismiss() { dismissal++; hide(); }

    private void showAtAnchor() {
        var b = anchor.localToScreen(anchor.getBoundsInLocal());
        if (b != null) {
            owner = anchor.getScene().getWindow();
            double x = Math.max(owner.getX() + 8, Math.min(b.getMinX(), owner.getX() + owner.getWidth() - body.getPrefWidth() - 8));
            show(owner, x, b.getMaxY() + 8);
        }
    }

    private void position() {
        if (!isShowing() || anchor == null) return;
        var b = anchor.localToScreen(anchor.getBoundsInLocal());
        if (b == null) return;
        setX(Math.max(owner.getX() + 8, Math.min(b.getMinX(), owner.getX() + owner.getWidth() - body.getPrefWidth() - 8)));
        setY(b.getMaxY() + 8);
    }
}
