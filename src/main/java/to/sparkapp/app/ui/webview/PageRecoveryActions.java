package to.sparkapp.app.ui.webview;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;

final class PageRecoveryActions extends HBox {
    private final Button retry = new Button("Retry");
    private final Button editSite = new Button("Edit site");
    private final Button showPage = new Button("Show page");

    PageRecoveryActions() {
        super(10);
        setAlignment(Pos.CENTER);
        getChildren().addAll(retry, editSite, showPage);
        retry.setStyle("-fx-background-color: #bfd6ab; -fx-text-fill: #252c25; -fx-background-radius: 7;"
                + " -fx-padding: 8 18;");

        for (var button : new Button[]{editSite, showPage}) {
            button.setStyle("-fx-background-color: #30342f; -fx-text-fill: #d7dfd0; -fx-background-radius: 7;"
                    + " -fx-padding: 8 18;");
        }

        for (var button : new Button[]{retry, editSite, showPage}) {
            button.managedProperty().bind(button.visibleProperty());
        }
    }

    void configure(Runnable onRetry, Runnable onEditSite, Runnable onShowPage) {
        configure(retry, onRetry);
        configure(editSite, onEditSite);
        configure(showPage, onShowPage);
    }

    private void configure(Button button, Runnable action) {
        button.setVisible(action != null);
        button.setOnAction(action == null ? null : event -> action.run());
    }
}
