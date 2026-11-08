package to.sparkapp.app.browser;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.GDI32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Bounds;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import lombok.Setter;

/** Sizes, positions, and rounds the popover; size changes after the first one are animated. */
final class ExtensionPopupPlacement {
    static final int CONTENT_INSET = 6;
    private static final int MIN_WIDTH = 120;
    private static final int MIN_HEIGHT = 48;
    private static final int MAX_WIDTH = 800;
    private static final int MAX_HEIGHT = 720;
    private static final int SCREEN_MARGIN = 8;
    private static final int ANCHOR_GAP = 6;
    private static final int UNANCHORED_TOP_OFFSET = 40;
    private static final int CORNER_RADIUS = 16;
    private static final int ANIMATION_FRAMES = 10;
    private static final int FRAME_MILLIS = 16;

    private final Stage window;
    private final Bounds anchor;
    private final Window owner;
    private final Rectangle2D screen;
    private final ExtensionPopupDiagnostics diagnostics;
    @Setter
    private Runnable viewportChanged = () -> {};
    @Setter
    private Runnable transitionFinished = () -> {};
    private int roundedWidth;
    private int roundedHeight;
    private Timeline transition;
    private boolean sized;

    ExtensionPopupPlacement(Stage window, Window owner, Bounds anchor, ExtensionPopupDiagnostics diagnostics) {
        this.window = window;
        this.owner = owner;
        this.anchor = anchor;
        this.diagnostics = diagnostics;

        var x = anchor == null ? owner.getX() : anchor.getMinX();
        var y = anchor == null ? owner.getY() : anchor.getMinY();
        screen = Screen.getScreensForRectangle(x, y, 1, 1).stream()
                .findFirst()
                .orElse(Screen.getPrimary())
                .getVisualBounds();
        window.setWidth(Math.min(window.getWidth(), screen.getWidth() - 2 * SCREEN_MARGIN));
        window.setHeight(Math.min(window.getHeight(), screen.getHeight() - 2 * SCREEN_MARGIN));
    }

    void resize(ExtensionPopupSize size, long handle) {
        var width = hostSize(size.width() * size.ratio() / window.getOutputScaleX(), MIN_WIDTH, MAX_WIDTH, screen.getWidth());
        var height = hostSize(size.height() * size.ratio() / window.getOutputScaleY(), MIN_HEIGHT, MAX_HEIGHT, screen.getHeight());
        if (Math.abs(window.getWidth() - width) <= 1 && Math.abs(window.getHeight() - height) <= 1) {
            return;
        }

        stop();
        diagnostics.resized(size, width, height);
        if (sized) {
            animate(width, height, handle);
        } else {
            sized = true;
            apply(width, height, handle);
        }
    }

    boolean isResizing() {
        return transition != null && transition.getStatus() == Animation.Status.RUNNING;
    }

    void stop() {
        if (transition != null) {
            transition.stop();
        }
    }

    void position() {
        var x = anchor == null ? owner.getX() + (owner.getWidth() - window.getWidth()) / 2 : anchor.getMinX();
        var y = anchor == null ? owner.getY() + UNANCHORED_TOP_OFFSET : anchor.getMaxY() + ANCHOR_GAP;
        if (anchor != null && y + window.getHeight() > screen.getMaxY() - SCREEN_MARGIN) {
            y = anchor.getMinY() - window.getHeight() - ANCHOR_GAP;
        }

        window.setX(Math.clamp(x, screen.getMinX() + SCREEN_MARGIN, screen.getMaxX() - window.getWidth() - SCREEN_MARGIN));
        window.setY(Math.clamp(y, screen.getMinY() + SCREEN_MARGIN, screen.getMaxY() - window.getHeight() - SCREEN_MARGIN));
    }

    /** Converts a document size to the host size, including the inset, within the limits and the screen. */
    private static double hostSize(double content, int minimum, int maximum, double screenSize) {
        var limit = Math.min(maximum, screenSize - 2 * SCREEN_MARGIN);
        return Math.clamp(Math.ceil(content) + 2 * CONTENT_INSET, minimum, limit);
    }

    /** Ease-out cubic over about 160 ms. */
    private void animate(double width, double height, long handle) {
        var startWidth = window.getWidth();
        var startHeight = window.getHeight();
        transition = new Timeline();
        for (var frame = 1; frame <= ANIMATION_FRAMES; frame++) {
            var progress = 1 - Math.pow(1 - frame / (double) ANIMATION_FRAMES, 3);
            var frameWidth = startWidth + (width - startWidth) * progress;
            var frameHeight = startHeight + (height - startHeight) * progress;
            transition.getKeyFrames().add(new KeyFrame(Duration.millis(frame * FRAME_MILLIS),
                    event -> apply(frameWidth, frameHeight, handle)));
        }
        transition.setOnFinished(event -> transitionFinished.run());
        transition.play();
    }

    private void apply(double width, double height, long handle) {
        window.setWidth(Math.round(width));
        window.setHeight(Math.round(height));
        position();
        roundCorners(handle);
        viewportChanged.run();
    }

    /** Uses the requested size, because Glass applies native bounds asynchronously. */
    private void roundCorners(long handle) {
        var width = (int) Math.ceil(window.getWidth() * window.getOutputScaleX());
        var height = (int) Math.ceil(window.getHeight() * window.getOutputScaleY());
        if (width == roundedWidth && height == roundedHeight) {
            return;
        }

        var radius = (int) Math.ceil(CORNER_RADIUS * window.getOutputScaleX());
        var region = GDI32.INSTANCE.CreateRoundRectRgn(0, 0, width + 1, height + 1, radius, radius);
        if (User32.INSTANCE.SetWindowRgn(new HWND(new Pointer(handle)), region, true) == 0) {
            GDI32.INSTANCE.DeleteObject(region);
        } else {
            roundedWidth = width;
            roundedHeight = height;
        }
    }
}
