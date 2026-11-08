package to.sparkapp.app.ui.tutorial;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Polyline;
import javafx.stage.Popup;
import javafx.stage.Screen;
import javafx.stage.Window;

/**
 * A tour bubble below a control of the Spark bar, with an arrow pointing at it. It is a window of
 * its own, because nothing can be drawn over the page.
 */
final class TutorialBubble extends Popup {
    static final double WIDTH = 300;
    private static final double SHADOW = 14;
    private static final double ARROW_WIDTH = 16;
    private static final double ARROW_HEIGHT = 8;
    private static final double EDGE = 12;
    private static final double GAP = 4;

    private final Label counter = new Label();
    private final Label title = new Label();
    private final Label text = new Label();
    private final Button secondary = new Button();
    private final Button primary = new Button();
    private final Group arrow = createArrow();
    private final VBox card;
    private Bounds cardBounds = new BoundingBox(0, 0, 0, 0);

    TutorialBubble() {
        counter.getStyleClass().add("tutorial-counter");
        title.getStyleClass().add("tutorial-title");
        text.getStyleClass().add("tutorial-text");
        text.setWrapText(true);
        text.setMinHeight(Region.USE_PREF_SIZE);
        secondary.getStyleClass().add("quiet-button");
        primary.getStyleClass().add("primary-button");
        primary.setDefaultButton(true);
        // The bubble is clicked, not tabbed through; a focus ring would only frame a button.
        secondary.setFocusTraversable(false);
        primary.setFocusTraversable(false);

        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        var buttons = new HBox(8, secondary, spacer, primary);
        buttons.setAlignment(Pos.CENTER_LEFT);
        secondary.managedProperty().bind(secondary.visibleProperty());

        card = new VBox(8, counter, title, text, buttons);
        card.getStyleClass().add("tutorial-bubble");
        card.setPrefWidth(WIDTH);
        card.setMinWidth(WIDTH);
        card.setMaxWidth(WIDTH);
        card.setLayoutY(ARROW_HEIGHT - 1);

        var canvas = new Pane(card, arrow);
        var body = new StackPane(canvas);
        body.setPadding(new Insets(0, SHADOW, SHADOW, SHADOW));
        body.setStyle("-fx-background-color: transparent;");
        body.getStylesheets().add(getClass().getResource("/css/shell.css").toExternalForm());
        getContent().add(body);
        setAutoHide(false);
    }

    /**
     * Shows a step below its control.
     *
     * @return where the card is on screen, so the guide can stand next to it
     */
    Bounds present(Window owner, TutorialStep step, int number, int count, Runnable onPrimary,
                   Runnable onSecondary) {
        counter.setText(number + " of " + count);
        title.setText(step.title());
        text.setText(step.text());
        primary.setText(step.primary());
        primary.setOnAction(event -> onPrimary.run());
        secondary.setVisible(step.secondary() != null);
        secondary.setText(step.secondary() == null ? "" : step.secondary());
        secondary.setOnAction(event -> onSecondary.run());

        return place(owner, step);
    }

    /** Follows the control, for example while the Spark window moves. */
    Bounds place(Window owner, TutorialStep step) {
        var target = step.target().localToScreen(step.target().getBoundsInLocal());

        if (target == null) {
            return cardBounds;
        }

        var screen = Screen.getScreensForRectangle(owner.getX(), owner.getY(), owner.getWidth(), owner.getHeight())
                .stream().findFirst().orElse(Screen.getPrimary()).getVisualBounds();
        var left = Math.max(owner.getX(), screen.getMinX()) + EDGE;
        var right = Math.min(owner.getX() + owner.getWidth(), screen.getMaxX()) - EDGE - WIDTH;
        var cardX = Math.clamp(target.getCenterX() - WIDTH / 2, left, Math.max(left, right));
        var cardY = target.getMaxY() + GAP + ARROW_HEIGHT;
        arrow.setLayoutX(Math.clamp(target.getCenterX() - cardX - ARROW_WIDTH / 2, 12, WIDTH - ARROW_WIDTH - 12));

        if (isShowing()) {
            setX(cardX - SHADOW);
            setY(cardY - ARROW_HEIGHT);
        } else {
            show(owner, cardX - SHADOW, cardY - ARROW_HEIGHT);
        }

        cardBounds = new BoundingBox(cardX, cardY, WIDTH, card.getHeight());

        return cardBounds;
    }

    String titleText() {
        return title.getText();
    }

    void pressPrimary() {
        primary.fire();
    }

    void pressSecondary() {
        secondary.fire();
    }

    String primaryText() {
        return primary.getText();
    }

    String secondaryText() {
        return secondary.isVisible() ? secondary.getText() : null;
    }

    Bounds cardBounds() {
        return cardBounds;
    }

    private static Group createArrow() {
        var fill = new Polygon(0, ARROW_HEIGHT, ARROW_WIDTH / 2, 0, ARROW_WIDTH, ARROW_HEIGHT);
        fill.setFill(Color.web("#2d2e33"));
        var outline = new Polyline(0, ARROW_HEIGHT, ARROW_WIDTH / 2, 0, ARROW_WIDTH, ARROW_HEIGHT);
        outline.setStroke(Color.web("#50515b"));
        outline.setFill(Color.TRANSPARENT);

        return new Group(fill, outline);
    }
}
