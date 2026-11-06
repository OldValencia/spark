package to.sparkapp.app.ui.topbar;

import javafx.scene.control.ListView;

final class SiteListScrolling {
    private SiteListScrolling() {
    }

    static void reveal(ListView<?> list, int index) {
        var contentHeight = list.getItems().size() * list.getFixedCellSize();
        var viewportHeight = list.getHeight() - list.getInsets().getTop() - list.getInsets().getBottom();

        // Scrolling a fully visible list can leave VirtualFlow positioned past its first rows.
        if (contentHeight > viewportHeight) {
            list.scrollTo(index);
        }
    }
}
