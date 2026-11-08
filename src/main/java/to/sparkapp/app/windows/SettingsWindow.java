package to.sparkapp.app.windows;

import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import lombok.RequiredArgsConstructor;
import to.sparkapp.app.ui.settings.SettingsPanel;
import to.sparkapp.app.ui.topbar.TopBarArea;

/**
 * Settings use the same contained surface as the shell's other owned panels.
 */
@RequiredArgsConstructor
public class SettingsWindow {
    private final Window owner;
    private final VBox panel;
    private final String caption;
    private Stage window;

    public SettingsWindow(Window owner, SettingsPanel panel) {
        this(owner, panel, "Settings");
    }

    public void open() {
        if (window == null) {
            createWindow();
        }

        double width = Math.min(600, owner.getWidth() - 24);
        double height = Math.min(610, owner.getHeight() - TopBarArea.HEIGHT - 24);
        panel.setPrefWidth(width - 12);
        panel.setMinWidth(0);
        panel.setMaxWidth(Double.MAX_VALUE);
        window.setWidth(width);
        window.setHeight(height);
        window.setX(owner.getX() + (owner.getWidth() - width) / 2);
        window.setY(owner.getY() + TopBarArea.HEIGHT + 8);
        window.show();
        window.toFront();
    }

    public void close() {
        if (window != null) {
            window.hide();
        }
    }

    public boolean isOpen() {
        return isShowing();
    }

    public boolean isShowing() {
        return window != null && window.isShowing();
    }

    private void createWindow() {
        window = new Stage(StageStyle.TRANSPARENT);
        window.initOwner(owner);
        window.setTitle("Spark " + caption);

        if (owner instanceof Stage stage) {
            window.setAlwaysOnTop(stage.isAlwaysOnTop());
            stage.alwaysOnTopProperty().addListener((o, before, pinned) -> window.setAlwaysOnTop(pinned));
        }

        var title = new Label(caption);
        var space = new Region();
        HBox.setHgrow(space, Priority.ALWAYS);

        var close = TopBarArea.button("x-mark", "Close " + caption.toLowerCase());
        close.setOnAction(e -> close());

        var heading = new HBox(title, space, close);
        heading.getStyleClass().add("settings-heading");

        var scroll = new ScrollPane(panel);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("settings-scroll");

        var root = new VBox(heading, scroll);
        root.getStyleClass().add("settings-surface");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        var clip = new Rectangle();
        clip.widthProperty().bind(root.widthProperty());
        clip.heightProperty().bind(root.heightProperty());
        clip.setArcWidth(26);
        clip.setArcHeight(26);
        root.setClip(clip);

        var scene = new Scene(root, Color.TRANSPARENT);
        scene.getStylesheets().addAll(getClass().getResource("/css/shell.css").toExternalForm(),
                getClass().getResource("/css/settings.css").toExternalForm());
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                close();
                e.consume();
            }
        });
        window.setScene(scene);
    }
}
