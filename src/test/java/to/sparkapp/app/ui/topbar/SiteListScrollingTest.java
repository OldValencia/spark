package to.sparkapp.app.ui.topbar;

import java.util.stream.IntStream;

import javafx.collections.FXCollections;
import javafx.scene.Scene;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import to.sparkapp.app.support.JavaFxTestRuntime;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("native")
@Timeout(20)
class SiteListScrollingTest {
    @Test
    void revealingTheLastRowKeepsAShortListFullyVisible() throws Exception {
        verifyLastRow(4);
    }

    @Test
    void revealingTheLastRowStillScrollsALongList() throws Exception {
        verifyLastRow(12);
    }

    private void verifyLastRow(int count) throws Exception {
        var list = JavaFxTestRuntime.call(() -> {
            var rows = IntStream.range(0, count).boxed().toList();
            var view = new ListView<>(FXCollections.observableArrayList(rows));
            view.setFixedCellSize(54);
            view.setStyle("-fx-padding: 0;");
            var stage = new Stage();
            stage.setScene(new Scene(new StackPane(view), 360, 216));
            stage.setX(-20000);
            stage.setY(-20000);
            stage.show();
            stage.getScene().getRoot().applyCss();
            stage.getScene().getRoot().layout();
            return view;
        });

        try {
            JavaFxTestRuntime.call(() -> {
                SiteListScrolling.reveal(list, count - 1);
                return null;
            });
            Thread.sleep(150);
            JavaFxTestRuntime.call(() -> {
                var viewport = list.lookup(".clipped-container");
                var bounds = viewport.localToScene(viewport.getBoundsInLocal());
                var last = list.lookupAll(".list-cell").stream().filter(ListCell.class::isInstance)
                        .map(ListCell.class::cast).filter(cell -> cell.getIndex() == count - 1)
                        .findFirst().orElseThrow();
                var row = last.localToScene(last.getBoundsInLocal());
                assertTrue(row.getMinY() >= bounds.getMinY() - 1);
                assertTrue(row.getMaxY() <= bounds.getMaxY() + 1);
                return null;
            });
        } finally {
            JavaFxTestRuntime.call(() -> {
                ((Stage) list.getScene().getWindow()).close();
                return null;
            });
        }
    }
}
