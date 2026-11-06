package to.sparkapp.app.ui.topbar;

import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableBooleanValue;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import to.sparkapp.app.ui.topbar.components.ShellIcon;

final class ShellSettingRow extends Button {
    private static final PseudoClass ON = PseudoClass.getPseudoClass("on");

    ShellSettingRow(String icon, String title, ObservableBooleanValue enabled, Runnable change) {
        var status = createStatus(enabled);
        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        var content = new HBox(10, ShellIcon.create(icon), new Label(title), spacer, status);
        content.setAlignment(Pos.CENTER_LEFT);
        content.prefWidthProperty().bind(widthProperty().subtract(14));
        setGraphic(content);
        getStyleClass().add("shell-row");
        setMaxWidth(Double.MAX_VALUE);
        accessibleTextProperty().bind(Bindings.concat(title, ": ", status.textProperty()));
        setOnAction(event -> change.run());
    }

    private Label createStatus(ObservableBooleanValue enabled) {
        var status = new Label();
        status.getStyleClass().add("menu-status");
        status.textProperty().bind(Bindings.when(enabled).then("On").otherwise("Off"));
        status.pseudoClassStateChanged(ON, enabled.get());
        enabled.addListener((observable, previous, value) -> status.pseudoClassStateChanged(ON, value));

        return status;
    }
}
