package to.sparkapp.app.ui.topbar;

import java.util.Comparator;

import javafx.scene.input.TransferMode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
final class SiteListDrag {
    private final SiteListContext context;
    private String draggedId;
    private String dropTargetId;
    private boolean dropAfter;
    @Getter
    private boolean preserveScroll;

    void start(String siteId) {
        draggedId = siteId;
    }

    void cancel() {
        draggedId = null;
        resetPreview();
    }

    void install() {
        context.list().setOnDragExited(e -> {
            if (!context.list().getBoundsInLocal()
                    .contains(context.list().sceneToLocal(e.getSceneX(), e.getSceneY()))) {
                resetPreview();
            }
        });
        context.list().setOnDragOver(e -> {
            if (draggedId == null || !context.search().getText().isBlank()) {
                return;
            }

            e.acceptTransferModes(TransferMode.MOVE);
            previewReorder(e.getSceneY());

            var position = context.list().sceneToLocal(e.getSceneX(), e.getSceneY());
            int target = -1;

            for (int i = 0; i < context.list().getItems().size(); i++) {
                if (context.list().getItems().get(i).id().equals(dropTargetId)) {
                    target = i;
                }
            }

            if (target >= 0 && position.getY() < 30) {
                context.list().scrollTo(Math.max(0, target - 1));
            } else if (target >= 0 && position.getY() > context.list().getHeight() - 30) {
                context.list().scrollTo(Math.min(context.list().getItems().size() - 1, target + 1));
            }

            e.consume();
        });
        context.list().setOnDragDropped(e -> {
            if (draggedId == null || dropTargetId == null) {
                return;
            }

            var id = draggedId;
            var target = dropTargetId;
            boolean after = dropAfter;
            draggedId = null;
            resetPreview();
            preserveScroll = true;

            try {
                context.dock().reorder(id, target, after);
            } finally {
                preserveScroll = false;
            }

            e.setDropCompleted(true);
            e.consume();
        });
    }

    private void previewReorder(double sceneY) {
        var cells = context.list().lookupAll(".list-cell").stream().filter(n -> n instanceof SiteListCell)
                .map(n -> (SiteListCell) n).filter(cell -> !cell.isEmpty() && cell.getItem() != null)
                .sorted(Comparator.comparingInt(SiteListCell::getIndex)).toList();

        if (cells.isEmpty()) {
            return;
        }

        var target = cells.getLast();
        dropAfter = true;

        for (var cell : cells) {
            double originalTop = cell.localToScene(0, 0).getY() - cell.getTranslateY();

            if (sceneY < originalTop + cell.getHeight() / 2) {
                target = cell;
                dropAfter = false;
                break;
            }
        }

        dropTargetId = target.getItem().id();

        int from = -1;

        for (int i = 0; i < context.list().getItems().size(); i++) {
            if (context.list().getItems().get(i).id().equals(draggedId)) {
                from = i;
            }
        }

        if (from < 0) {
            return;
        }

        int to = target.getIndex() + (dropAfter ? 1 : 0);

        if (to > from) {
            to--;
        }

        for (var cell : cells) {
            int i = cell.getIndex();
            double shift = 0;

            if (from < to && i > from && i <= to) {
                shift = -context.list().getFixedCellSize();
            } else if (from > to && i >= to && i < from) {
                shift = context.list().getFixedCellSize();
            }

            cell.animateTo(shift);

            boolean isTarget = cell.getItem().id().equals(dropTargetId);
            cell.pseudoClassStateChanged(cell.before, isTarget && !dropAfter);
            cell.pseudoClassStateChanged(cell.after, isTarget && dropAfter);
        }
    }

    void resetPreview() {
        dropTargetId = null;
        context.list().lookupAll(".list-cell").stream().filter(n -> n instanceof SiteListCell)
                .map(n -> (SiteListCell) n).forEach(SiteListCell::resetPosition);
    }
}
