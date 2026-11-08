package to.sparkapp.app.readme;

import java.util.Optional;
import java.util.function.Predicate;

import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Labeled;
import javafx.stage.Window;

/** Finds visible Spark controls by their accessible or visible text and reports screen positions. */
final class ReadmeUi {
    private ReadmeUi() {
    }

    /** Screen center of the first visible control whose accessible text or label matches. */
    static Point2D control(String text) throws Exception {
        return center(named(text));
    }

    static Point2D center(Predicate<Node> match) throws Exception {
        return center(match, 10_000);
    }

    /** Like {@link #center(Predicate)}, but returns null when nothing matches within the timeout. */
    static Point2D tryCenter(Predicate<Node> match, long timeoutMillis) throws Exception {
        var deadline = System.nanoTime() + timeoutMillis * 1_000_000L;
        while (System.nanoTime() < deadline) {
            var found = ReadmeFx.call(() -> find(match).map(ReadmeUi::screenCenter).orElse(null));
            if (found != null) {
                return found;
            }
            ReadmeFx.pause(100);
        }

        return null;
    }

    static Predicate<Node> named(String text) {
        return node -> text.equals(node.getAccessibleText()) || node instanceof Labeled labeled && text.equals(labeled.getText());
    }

    private static Point2D center(Predicate<Node> match, long timeoutMillis) throws Exception {
        var deadline = System.nanoTime() + timeoutMillis * 1_000_000L;
        while (System.nanoTime() < deadline) {
            var found = ReadmeFx.call(() -> find(match).map(ReadmeUi::screenCenter).orElse(null));
            if (found != null) {
                return found;
            }
            ReadmeFx.pause(100);
        }
        throw new IllegalStateException("A control needed for the README capture is not visible. Visible: "
                + ReadmeFx.call(ReadmeUi::describeVisible));
    }

    static Optional<Node> find(Predicate<Node> match) {
        for (var window : Window.getWindows()) {
            if (!window.isShowing() || window.getScene() == null) {
                continue;
            }
            var found = search(window.getScene().getRoot(), match);
            if (found.isPresent()) {
                return found;
            }
        }

        return Optional.empty();
    }

    /** Names of visible controls in every showing window, for diagnosing a failed capture step. */
    static String describeVisible() {
        var names = new StringBuilder();
        for (var window : Window.getWindows()) {
            if (window.isShowing() && window.getScene() != null) {
                names.append(System.lineSeparator()).append('[').append(window.getClass().getSimpleName()).append("] ");
                collect(window.getScene().getRoot(), names);
            }
        }

        return names.toString();
    }

    private static void collect(Node node, StringBuilder names) {
        if (!node.isVisible()) {
            return;
        }
        if (node.getAccessibleText() != null) {
            names.append(node.getAccessibleText()).append(node.isDisabled() ? " (disabled)" : "").append(" | ");
        } else if (node instanceof Labeled labeled && labeled.getText() != null && !labeled.getText().isBlank()) {
            names.append('"').append(labeled.getText()).append("\" | ");
        }
        if (node instanceof Parent parent) {
            for (var child : parent.getChildrenUnmodifiable()) {
                collect(child, names);
            }
        }
    }

    private static Optional<Node> search(Node node, Predicate<Node> match) {
        if (!node.isVisible() || node.isDisabled()) {
            return Optional.empty();
        }
        if (match.test(node) && node.localToScreen(node.getBoundsInLocal()) != null) {
            return Optional.of(node);
        }
        if (node instanceof Parent parent) {
            for (var child : parent.getChildrenUnmodifiable()) {
                var found = search(child, match);
                if (found.isPresent()) {
                    return found;
                }
            }
        }

        return Optional.empty();
    }

    private static Point2D screenCenter(Node node) {
        var bounds = node.localToScreen(node.getBoundsInLocal());
        return new Point2D(bounds.getCenterX(), bounds.getCenterY());
    }
}
