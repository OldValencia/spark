package to.sparkapp.app.ui.dialogs;

import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class DialogPlacement {
    /** Centers a dialog over its owner while keeping it on the owner's screen. */
    static void centerOver(Stage dialog, Window owner) {
        var screen = Screen.getScreensForRectangle(owner.getX(), owner.getY(), owner.getWidth(), owner.getHeight())
                .stream().findFirst().orElse(Screen.getPrimary()).getVisualBounds();
        dialog.setX(Math.clamp(owner.getX() + (owner.getWidth() - dialog.getWidth()) / 2, screen.getMinX(),
                Math.max(screen.getMinX(), screen.getMaxX() - dialog.getWidth())));
        dialog.setY(Math.clamp(owner.getY() + (owner.getHeight() - dialog.getHeight()) / 2, screen.getMinY(),
                Math.max(screen.getMinY(), screen.getMaxY() - dialog.getHeight())));
    }
}
