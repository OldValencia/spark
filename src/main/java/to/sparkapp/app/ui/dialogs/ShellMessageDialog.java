package to.sparkapp.app.ui.dialogs;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

/**
 * A short message in Spark's own dialog style: a title, a few lines, and one main button with an
 * optional second action.
 */
public final class ShellMessageDialog extends Stage {
    private static final double WIDTH = 400;

    private final VBox content = new VBox(10);
    private final HBox buttons = new HBox(8);
    private final Button close;

    public ShellMessageDialog(Window owner, String title, String closeLabel) {
        initOwner(owner);
        initModality(Modality.WINDOW_MODAL);
        initStyle(StageStyle.TRANSPARENT);
        setTitle(title);
        setResizable(false);

        var heading = new Label(title);
        heading.getStyleClass().add("dialog-title");
        heading.setWrapText(true);

        close = new Button(closeLabel);
        close.getStyleClass().add("primary-button");
        close.setDefaultButton(true);
        close.setCancelButton(true);
        close.setOnAction(event -> close());

        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        buttons.getChildren().addAll(spacer, close);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        var panel = new VBox(18, heading, content, buttons);
        panel.getStyleClass().addAll("shell-dialog", "preset-name-dialog");
        panel.setPrefWidth(WIDTH);
        var root = new StackPane(panel);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: transparent;");

        var scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add(getClass().getResource("/css/shell.css").toExternalForm());
        setScene(scene);
        setOnShown(event -> {
            DialogPlacement.centerOver(this, owner);
            close.requestFocus();
        });
    }

    public ShellMessageDialog text(String text) {
        content.getChildren().add(wrapped(text, "dialog-text"));

        return this;
    }

    public ShellMessageDialog hint(String text) {
        content.getChildren().add(wrapped(text, "field-hint"));

        return this;
    }

    /** A value such as a folder path, shown in a field so it can be selected and copied. */
    public ShellMessageDialog copyable(String value) {
        var field = new TextField(value);
        field.setEditable(false);
        field.setFocusTraversable(false);
        field.getStyleClass().add("dialog-value");
        content.getChildren().add(field);

        return this;
    }

    /** A second action next to the main button, which closes the dialog first. */
    public ShellMessageDialog action(String label, Runnable command) {
        var button = new Button(label);
        button.getStyleClass().add("quiet-button");
        button.setOnAction(event -> {
            close();
            command.run();
        });
        buttons.getChildren().add(1, button);

        return this;
    }

    private static Label wrapped(String text, String styleClass) {
        var label = new Label(text);
        label.getStyleClass().add(styleClass);
        label.setWrapText(true);
        label.setMaxWidth(WIDTH - 44);
        // Wrapped text needs its full height in a VBox, or it is cut with an ellipsis.
        label.setMinHeight(Region.USE_PREF_SIZE);

        return label;
    }
}
