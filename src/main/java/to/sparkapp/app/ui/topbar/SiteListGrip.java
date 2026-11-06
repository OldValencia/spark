package to.sparkapp.app.ui.topbar;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import to.sparkapp.app.config.AiConfiguration.AiConfig;

final class SiteListGrip extends Button {
    private final SiteListContext context;
    private final SiteListDrag drag;
    private final SiteListCell cell;
    private final AiConfig site;

    SiteListGrip(SiteListContext context, SiteListDrag drag, SiteListCell cell, AiConfig site) {
        super("⠿");
        this.context = context;
        this.drag = drag;
        this.cell = cell;
        this.site = site;
        getStyleClass().add("shell-button");
        setUserData(site.id());
        setAccessibleText("Reorder " + site.name());
        setDisable(!context.search().getText().isBlank());
        setOnDragDetected(this::startDrag);
        setOnDragDone(event -> drag.cancel());
        setOnKeyPressed(this::moveWithKeyboard);
    }

    private void startDrag(MouseEvent event) {
        drag.start(site.id());

        var board = startDragAndDrop(TransferMode.MOVE);
        board.setDragView(cell.getGraphic().snapshot(null, null), event.getX(), event.getY());
        cell.setOpacity(0);

        var content = new ClipboardContent();
        content.putString(site.id());
        board.setContent(content);
        event.consume();
    }

    private void moveWithKeyboard(KeyEvent event) {
        if (!event.isAltDown() || (event.getCode() != KeyCode.UP && event.getCode() != KeyCode.DOWN)) {
            return;
        }

        var index = cell.getIndex();
        var target = index + (event.getCode() == KeyCode.UP ? -1 : 1);

        if (target >= 0 && target < context.list().getItems().size()) {
            context.dock().reorder(site.id(), context.list().getItems().get(target).id(), target > index);
            context.list().getSelectionModel().select(target);
            context.list().scrollTo(target);
            Platform.runLater(this::restoreFocus);
        }

        event.consume();
    }

    private void restoreFocus() {
        context.list().lookupAll(".shell-button").stream().filter(node -> site.id().equals(node.getUserData()))
                .findFirst().ifPresent(Node::requestFocus);
    }
}
