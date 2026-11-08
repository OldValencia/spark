package to.sparkapp.app.browser;

import java.util.UUID;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Duration;
import lombok.AccessLevel;
import lombok.Getter;
import to.sparkapp.app.utils.NativeWindowUtils;
import to.sparkapp.app.utils.SystemUtils;

/** Hosts the extension's own document without Spark's page scripts or Java bridge. Only one is open at a time. */
public final class ExtensionPopup {
    /** Cheap native checks only; document size changes are pushed by the page. */
    private static final Duration PULSE_INTERVAL = Duration.millis(200);
    private static final int LOADING_WIDTH = 420;
    private static final int LOADING_HEIGHT = 600;
    private static final int ERROR_HEIGHT = 160;
    /** The global mouse hook can report the press slightly before JavaFX does. */
    private static final long DISMISSAL_GRACE_NANOS = 300_000_000L;
    private static ExtensionPopup active;
    private static String lastClosedContent;
    private static long lastClosedAt;

    private final ExtensionPopupDiagnostics diagnostics = new ExtensionPopupDiagnostics();
    private final ChangeListener<Boolean> ownerVisibility = (observable, previous, showing) -> {
        if (!showing) {
            this.window.close();
        }
    };
    @Getter(AccessLevel.PACKAGE)
    private Stage window;
    private Window owner;
    private ExtensionPopupBrowser content;
    private ExtensionPopupPlacement placement;
    private ExtensionPopupDismissal dismissal;
    private Timeline pulse;
    private long handle;
    private boolean closed;
    private boolean presented;
    private String shownPage;

    /** Prepares a hidden browser so the next popup opens without waiting for browser creation. */
    public static void prepareForOpening() {
        if (SystemUtils.isWindows()) {
            ExtensionPopupWebviews.keepSpare();
        }
    }

    /** Releases the prepared browser when popups can no longer be opened, for example when the manager closes. */
    public static void stopPreparing() {
        ExtensionPopupWebviews.discardSpare();
    }

    /**
     * Makes the popup's button a toggle: closes the popup if it shows this page, and reports whether
     * it was showing it, including when the same click already dismissed it as an outside click.
     *
     * @param pressedAt {@link System#nanoTime()} of the mouse press that started the click
     */
    public static boolean closeIfShowing(String id, String page, long pressedAt) {
        var key = contentKey(id, page);
        if (active != null && key.equals(active.shownPage)) {
            active.window.close();
            return true;
        }

        return key.equals(lastClosedContent) && lastClosedAt >= pressedAt - DISMISSAL_GRACE_NANOS;
    }

    public void openAnchored(Node anchor, String name, String id, String page, int contextWindowId) {
        if (SystemUtils.isWindows()) {
            var bounds = anchor.localToScreen(anchor.getBoundsInLocal());
            open(anchor.getScene().getWindow(), bounds, name, id, page, contextWindowId);
        }
    }

    /** Opens near the owner's top edge; a null context window leaves tab queries native. */
    public void open(Window owner, String name, String id, String page, Integer contextWindowId) {
        if (SystemUtils.isWindows()) {
            open(owner, null, name, id, page, contextWindowId);
        }
    }

    Webview getBrowser() {
        return content == null ? null : content.getBrowser();
    }

    private void open(Window owner, Bounds anchor, String name, String id, String page, Integer contextWindowId) {
        if (active != null) {
            active.window.close();
        }
        active = this;
        this.owner = owner;
        shownPage = contentKey(id, page);

        window = createWindow(owner);
        placement = new ExtensionPopupPlacement(window, owner, anchor, diagnostics);
        placement.position();
        show(name);

        diagnostics.opened(name, id, window, handle);
        dismissal = new ExtensionPopupDismissal(window, handle);
        owner.showingProperty().addListener(ownerVisibility);
        content = createContent();
        startPulse();
        content.open(id, page, contextWindowId);
    }

    private static String contentKey(String id, String page) {
        return id + "/" + page;
    }

    private Stage createWindow(Window owner) {
        var stage = new Stage(StageStyle.UNDECORATED);
        stage.initOwner(owner);
        stage.setScene(new Scene(new StackPane(), LOADING_WIDTH, LOADING_HEIGHT));
        stage.setWidth(LOADING_WIDTH + 2 * ExtensionPopupPlacement.CONTENT_INSET);
        stage.setHeight(LOADING_HEIGHT + 2 * ExtensionPopupPlacement.CONTENT_INSET);
        stage.getScene().getRoot().setStyle("-fx-background-color:#25262a;");
        stage.getScene().setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                stage.close();
            }
        });
        if (owner instanceof Stage ownerStage) {
            stage.setAlwaysOnTop(ownerStage.isAlwaysOnTop());
        }
        stage.setOnHidden(event -> close());

        return stage;
    }

    /** Shows the window invisibly until its first measurement; a unique title locates its native handle. */
    private void show(String name) {
        window.setTitle("Spark extension " + UUID.randomUUID());
        window.setOpacity(0);
        window.show();
        handle = NativeWindowUtils.getJavaFXWindowHandle(window.getTitle());
        window.setTitle(name);
    }

    private ExtensionPopupBrowser createContent() {
        var browser = new ExtensionPopupBrowser(handle,
                size -> Platform.runLater(() -> resize(size)),
                error -> Platform.runLater(() -> showError(error)),
                () -> Platform.runLater(window::close),
                diagnostics);
        placement.setViewportChanged(browser::resizeViewport);
        placement.setTransitionFinished(browser::remeasure);

        return browser;
    }

    private void startPulse() {
        pulse = new Timeline(new KeyFrame(PULSE_INTERVAL, event -> {
            content.tick();
            dismissal.checkFocus();
        }));
        pulse.setCycleCount(Timeline.INDEFINITE);
        pulse.play();
    }

    private void resize(ExtensionPopupSize size) {
        if (closed || placement.isResizing()) {
            return;
        }

        placement.resize(size, handle);
        if (!presented) {
            presented = true;
            present();
        }
    }

    private void present() {
        window.setOpacity(1);
        window.requestFocus();
        dismissal.arm();
    }

    private void showError(Throwable error) {
        if (closed) {
            return;
        }

        diagnostics.failed(error);
        var label = new Label("The extension page could not open.\n" + error.getMessage());
        label.setWrapText(true);
        window.getScene().setRoot(new StackPane(label));
        placement.resize(new ExtensionPopupSize(LOADING_WIDTH, ERROR_HEIGHT, window.getOutputScaleX()), handle);
        present();
        pulse.stop();
    }

    private void close() {
        closed = true;
        diagnostics.closed(window);
        placement.stop();
        pulse.stop();
        dismissal.close();
        owner.showingProperty().removeListener(ownerVisibility);
        content.close();
        if (active == this) {
            active = null;
        }
        lastClosedContent = shownPage;
        lastClosedAt = System.nanoTime();
        ExtensionPopupWebviews.replenish();
    }
}
