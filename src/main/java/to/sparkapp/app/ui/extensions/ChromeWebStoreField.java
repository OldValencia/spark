package to.sparkapp.app.ui.extensions;

import java.util.function.Consumer;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import to.sparkapp.app.extensions.ChromeWebStore;

/** Accepts a Chrome Web Store link or extension ID and reports the recognized ID. */
final class ChromeWebStoreField extends HBox {
    private final TextField input = new TextField();

    ChromeWebStoreField(Consumer<String> install, Consumer<String> invalid) {
        super(8);
        setAlignment(Pos.CENTER_LEFT);
        input.setPromptText("Chrome Web Store link or extension ID");
        HBox.setHgrow(input, Priority.ALWAYS);

        var button = new Button("Install");
        button.getStyleClass().add("quiet-button");
        button.disableProperty().bind(input.textProperty().isEmpty());

        Runnable submit = () -> ChromeWebStore.extensionId(input.getText()).ifPresentOrElse(id -> {
            input.clear();
            install.accept(id);
        }, () -> invalid.accept("Paste a Chrome Web Store extension link or its 32-letter ID."));
        button.setOnAction(event -> submit.run());
        input.setOnAction(event -> submit.run());

        getChildren().addAll(input, button);
    }
}
