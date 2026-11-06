package to.sparkapp.app.ui.topbar.components;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import javafx.scene.Group;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.transform.Scale;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Small vector controls sourced from the bundled Heroicons outline assets.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ShellIcon {
    private static final Pattern PATH = Pattern.compile("\\sd=\"([^\"]+)\"");

    public static StackPane create(String name) {
        var group = new Group();

        try (var in = ShellIcon.class.getResourceAsStream("/ui-icons/" + name + ".svg")) {
            if (in == null) {
                throw new IllegalArgumentException("Unknown shell icon: " + name);
            }

            var paths = PATH.matcher(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            while (paths.find()) {
                var path = new SVGPath();
                path.setContent(paths.group(1));
                path.setFill(null);
                path.setStroke(Color.web("#bdbdc7"));
                path.setStrokeWidth(1.5);
                path.setStrokeLineCap(StrokeLineCap.ROUND);
                path.setStrokeLineJoin(StrokeLineJoin.ROUND);
                group.getChildren().add(path);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read shell icon", e);
        }

        group.getTransforms().add(new Scale(2.0 / 3, 2.0 / 3));
        group.setManaged(false);
        group.setLayoutX(1);
        group.setLayoutY(1);

        var pane = new StackPane(group);
        pane.setMinSize(18, 18);
        pane.setPrefSize(18, 18);
        pane.setMaxSize(18, 18);
        pane.setMouseTransparent(true);

        return pane;
    }
}
