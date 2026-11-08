package to.sparkapp.app.readme;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/** A plain dark backdrop behind Spark, with the chapter caption shown in the tour. */
final class ReadmeBackdrop {
    private final Stage stage = new Stage(StageStyle.UNDECORATED);
    private final Label title = new Label();
    private final Label subtitle = new Label();
    private final VBox caption = new VBox(6, title, subtitle);


    /** Must be created on the JavaFX thread. */
    ReadmeBackdrop() {
        title.setStyle("-fx-text-fill: #eeeef3; -fx-font-family: 'Segoe UI Semibold'; -fx-font-size: 24px;");
        subtitle.setStyle("-fx-text-fill: #a9a9b6; -fx-font-family: 'Segoe UI'; -fx-font-size: 16px;");
        caption.setPadding(new Insets(0, 60, 40, 0));
        caption.setAlignment(Pos.BOTTOM_RIGHT);

        var root = new StackPane(caption);
        root.setAlignment(Pos.BOTTOM_RIGHT);
        root.setStyle("-fx-background-color: linear-gradient(to bottom right, #202126, #16171b);");
        stage.setScene(new Scene(root));
        stage.setTitle("Spark README backdrop");
        // Above other applications so no desktop content is captured; Spark is raised above it.
        stage.setAlwaysOnTop(true);
    }

    void place(Rectangle2D region) {
        stage.setX(region.getMinX());
        stage.setY(region.getMinY());
        stage.setWidth(region.getWidth());
        stage.setHeight(region.getHeight());
        if (!stage.isShowing()) {
            stage.show();
        }
    }

    void caption(String number, String heading, String text) {
        caption.setVisible(heading != null);
        title.setText(heading == null ? "" : number + "  " + heading);
        subtitle.setText(text == null ? "" : text);
    }

    void close() {
        stage.close();
    }
}
