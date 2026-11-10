package to.sparkapp.app.windows;

import static org.junit.jupiter.api.Assertions.*;

import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Stage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import to.sparkapp.app.support.JavaFxTestRuntime;

@Tag("native")
@Timeout(20)
class PagePressWatchTest {
    @Test
    void onlyPressesOnThePageCountNotOnTheBarOrOnAMenuOverThePage() throws Exception {
        JavaFxTestRuntime.call(() -> {
            var bar = new Region();
            bar.setPrefSize(400, 50);
            var page = new StackPane();
            page.setPrefSize(400, 250);
            var stage = new Stage();
            stage.setScene(new Scene(new VBox(bar, page)));
            stage.setX(100);
            stage.setY(100);
            stage.show();

            var menuContent = new Region();
            menuContent.setPrefSize(120, 100);
            var menu = new Popup();
            menu.getContent().add(menuContent);
            var pageTopLeft = page.localToScreen(0, 0);
            menu.show(stage, pageTopLeft.getX() + 10, pageTopLeft.getY() + 10);
            var watch = new PagePressWatch(page, () -> {
            });

            try {
                var barPoint = bar.localToScreen(200, 25);

                assertTrue(watch.isPagePress(new Point2D(pageTopLeft.getX() + 300, pageTopLeft.getY() + 200)));
                assertFalse(watch.isPagePress(new Point2D(pageTopLeft.getX() + 30, pageTopLeft.getY() + 30)),
                        "A press inside the menu keeps the menu open");
                assertFalse(watch.isPagePress(barPoint), "Spark's own controls close menus by themselves");
            } finally {
                watch.close();
                menu.hide();
                stage.close();
            }

            return null;
        });
    }
}
