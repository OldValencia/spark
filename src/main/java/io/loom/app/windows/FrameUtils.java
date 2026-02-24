package io.loom.app.windows;

import javafx.scene.Node;
import javafx.stage.Stage;

public class FrameUtils {

    public static Stage getOwnerStage(Node currentNode) {
        if (currentNode != null && currentNode.getScene() != null) {
            var window = currentNode.getScene().getWindow();
            if (window instanceof Stage stage) {
                return stage;
            }
        }
        return null;
    }
}