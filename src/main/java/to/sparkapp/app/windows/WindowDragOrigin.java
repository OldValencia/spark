package to.sparkapp.app.windows;

import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

record WindowDragOrigin(double screenX, double screenY, double x, double y, double width, double height) {
    static WindowDragOrigin capture(Stage stage, MouseEvent event) {
        return new WindowDragOrigin(event.getScreenX(), event.getScreenY(), stage.getX(), stage.getY(),
                stage.getWidth(), stage.getHeight());
    }
}
