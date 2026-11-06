package to.sparkapp.app.ui.topbar;

import javafx.beans.binding.Bindings;
import javafx.css.PseudoClass;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import to.sparkapp.app.windows.WindowController;

final class WindowSizePresets extends HBox {
    private static final PseudoClass CURRENT = PseudoClass.getPseudoClass("current");

    WindowSizePresets(Stage frame, WindowController controller, Runnable dismiss) {
        super(6);

        for (var preset : Preset.values()) {
            var button = createButton(frame, preset);
            button.setOnAction(event -> {
                dismiss.run();
                controller.preset(preset.getWidth(), preset.getHeight());
            });
            getChildren().add(button);
        }
    }

    private Button createButton(Stage frame, Preset preset) {
        var button = new Button(preset.getLabel());
        button.getStyleClass().add("size-preset");
        button.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(button, Priority.ALWAYS);

        var matches = Bindings.createBooleanBinding(() -> Math.round(frame.getWidth()) == preset.getWidth() &&
                Math.round(frame.getHeight()) == preset.getHeight(), frame.widthProperty(), frame.heightProperty());
        button.getProperties().put("sizeMatch", matches);
        button.pseudoClassStateChanged(CURRENT, matches.get());
        matches.addListener((observable, previous, active) -> button.pseudoClassStateChanged(CURRENT, active));

        return button;
    }

    @Getter
    @RequiredArgsConstructor
    private enum Preset {
        SMALL("S", 700, 580), MEDIUM("M", 860, 700), LARGE("L", 1120, 820);

        private final String label;
        private final int width;
        private final int height;
    }
}
