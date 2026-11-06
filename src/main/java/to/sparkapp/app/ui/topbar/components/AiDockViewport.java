package to.sparkapp.app.ui.topbar.components;

import static to.sparkapp.app.ui.topbar.components.AiDock.DOCK_HEIGHT;
import static to.sparkapp.app.ui.topbar.components.AiDock.ITEM_MARGIN;
import static to.sparkapp.app.ui.topbar.components.AiDock.MAX_DOCK_WIDTH;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import lombok.Getter;

final class AiDockViewport extends StackPane {
    private final AiDock dock;
    @Getter
    private final HBox dockContainer;
    @Getter
    private final ScrollPane scrollPane;
    private final AiDockScrollArrow leftArrow;
    private final AiDockScrollArrow rightArrow;

    AiDockViewport(AiDock dock) {
        this.dock = dock;
        scrollPane = new ScrollPane();
        scrollPane.getStyleClass().add("shell-dock");
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setFitToHeight(true);
        scrollPane.setPannable(false);
        scrollPane.setMaxWidth(MAX_DOCK_WIDTH);
        scrollPane.setMinWidth(0);
        scrollPane.setMinHeight(DOCK_HEIGHT);
        scrollPane.setPrefHeight(DOCK_HEIGHT);
        scrollPane.setMaxHeight(DOCK_HEIGHT);

        dockContainer = new HBox();
        dockContainer.setSpacing(ITEM_MARGIN);
        dockContainer.setStyle("-fx-background-color: transparent;");
        dockContainer.setPadding(new Insets(4, 0, 4, 0));
        scrollPane.setContent(dockContainer);

        scrollPane.addEventFilter(ScrollEvent.SCROLL, e -> {
            double delta = Math.abs(e.getDeltaX()) > Math.abs(e.getDeltaY()) ? e.getDeltaX() : e.getDeltaY();

            if (delta != 0) {
                double overflow = dockContainer.getWidth() - scrollPane.getViewportBounds().getWidth();

                if (overflow > 0) {
                    scrollPane.setHvalue(Math.clamp(scrollPane.getHvalue() - delta / overflow, 0.0, 1.0));
                }
            }

            e.consume();
        });

        leftArrow = new AiDockScrollArrow(true, dock);
        rightArrow = new AiDockScrollArrow(false, dock);

        leftArrow.managedProperty().bind(leftArrow.visibleProperty());
        rightArrow.managedProperty().bind(rightArrow.visibleProperty());

        var rail = new HBox(leftArrow, scrollPane, rightArrow);
        rail.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(scrollPane, Priority.ALWAYS);
        getChildren().add(rail);
        scrollPane.hvalueProperty().addListener((obs, old, value) -> refreshArrowOpacity());
        dockContainer.widthProperty().addListener((obs, old, value) -> refreshArrowOpacity());
        dock.widthProperty().addListener((obs, old, value) -> refreshArrowOpacity());
        scrollPane.viewportBoundsProperty().addListener((obs, old, value) -> refreshArrowOpacity());

    }

    private boolean hasOverflow() {
        return dockContainer.getWidth() > dock.getWidth() + 2;
    }

    private boolean canScrollLeft() {
        return scrollPane.getHvalue() > 0.005;
    }

    private boolean canScrollRight() {
        return hasOverflow() && scrollPane.getHvalue() < 0.995;
    }

    public void refreshArrowOpacity() {
        boolean overflow = hasOverflow();
        leftArrow.setVisible(overflow);
        rightArrow.setVisible(overflow);
        leftArrow.setDisable(!canScrollLeft());
        rightArrow.setDisable(!canScrollRight());
    }

    void revealSelection(DockItemNode selectedNode) {
        Platform.runLater(() -> {
            if (selectedNode == null) {
                return;
            }

            dockContainer.applyCss();
            dockContainer.layout();

            double overflow = dockContainer.getWidth() - scrollPane.getViewportBounds().getWidth();

            if (overflow <= 0) {
                return;
            }

            double left = scrollPane.getHvalue() * overflow;
            double x = selectedNode.getLayoutX();

            if (x < left) {
                scrollPane.setHvalue(Math.clamp(x / overflow, 0, 1));
            } else if (x + selectedNode.getWidth() > left + scrollPane.getViewportBounds().getWidth()) {
                scrollPane.setHvalue(
                        Math.clamp((x + selectedNode.getWidth() - scrollPane.getViewportBounds().getWidth()) / overflow,
                                0, 1));
            }
        });
    }

    void cancel() {
        leftArrow.cancel();
        rightArrow.cancel();
    }
}
