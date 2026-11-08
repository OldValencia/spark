package to.sparkapp.app.ui.extensions;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

final class ExtensionLoadingStatus extends HBox {
    private final ProgressIndicator indicator = new ProgressIndicator();
    private final Label label = new Label();

    ExtensionLoadingStatus() {
        super(10);
        setAlignment(Pos.CENTER_LEFT);
        indicator.getStyleClass().add("extension-progress");
        indicator.setMinSize(18, 18);
        indicator.setPrefSize(18, 18);
        indicator.setMaxSize(18, 18);
        indicator.setStyle("-fx-progress-color: #8bafff;");
        indicator.managedProperty().bind(indicator.visibleProperty());
        label.setWrapText(true);
        label.getStyleClass().add("field-hint");
        HBox.setHgrow(label, Priority.ALWAYS);
        getChildren().addAll(indicator, label);
        setBusy(false);
    }

    void setBusy(boolean busy) {
        indicator.setVisible(busy);
    }

    void setText(String text) {
        label.setText(text);
    }
}
