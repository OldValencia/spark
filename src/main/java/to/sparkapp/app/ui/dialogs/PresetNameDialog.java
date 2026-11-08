package to.sparkapp.app.ui.dialogs;

import java.util.Optional;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import to.sparkapp.app.config.WindowSizePreset;

public final class PresetNameDialog extends Stage {
    private final TextField name = new TextField();
    private String result;

    public PresetNameDialog(Window owner, WindowSizePreset preset) {
        initOwner(owner);
        initModality(Modality.WINDOW_MODAL);
        initStyle(StageStyle.TRANSPARENT);
        setTitle("Rename size preset");
        setResizable(false);

        var title = new Label("Rename size preset");
        title.getStyleClass().add("dialog-title");
        var dimensions = new Label(preset.width() + " × " + preset.height());
        dimensions.getStyleClass().add("field-hint");
        var heading = new VBox(5, title, dimensions);

        name.setText(preset.name());
        name.setPromptText("For example, Reading");
        name.setTextFormatter(new TextFormatter<String>(change -> {
            var text = change.getControlNewText();

            return text.length() <= WindowSizePreset.MAX_NAME_LENGTH &&
                    text.codePoints().noneMatch(Character::isISOControl) ? change : null;
        }));
        var label = new Label("Preset name");
        label.getStyleClass().add("field-label");
        label.setLabelFor(name);

        var panel = new VBox(18, heading, new VBox(7, label, name), buttons());
        panel.getStyleClass().addAll("shell-dialog", "preset-name-dialog");
        panel.setPrefWidth(360);
        var root = new StackPane(panel);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: transparent;");
        var scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add(getClass().getResource("/css/shell.css").toExternalForm());
        setScene(scene);
        setOnShown(event -> {
            DialogPlacement.centerOver(this, owner);
            Platform.runLater(() -> {
                name.requestFocus();
                name.selectAll();
            });
        });
    }

    private HBox buttons() {
        var cancel = new Button("Cancel");
        cancel.getStyleClass().add("quiet-button");
        cancel.setCancelButton(true);
        cancel.setOnAction(event -> close());

        var save = new Button("Save");
        save.getStyleClass().add("primary-button");
        save.setDefaultButton(true);
        save.disableProperty().bind(Bindings.createBooleanBinding(() -> name.getText().isBlank(), name.textProperty()));
        save.setOnAction(event -> {
            result = name.getText().strip();
            close();
        });
        var buttons = new HBox(8, cancel, save);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        return buttons;
    }

    public Optional<String> showNameAndWait() {
        showAndWait();

        return Optional.ofNullable(result);
    }
}
