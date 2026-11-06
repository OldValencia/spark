package to.sparkapp.app.windows;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.ListChangeListener;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.animation.Interpolator;
import javafx.animation.PauseTransition;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Popup;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.scene.paint.Color;
import javafx.scene.robot.Robot;
import javafx.util.Duration;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.config.AppPreferences.WindowPlacement;
import to.sparkapp.app.utils.NativeWindowUtils;
import java.util.UUID;

/** Owns logical window geometry; browser sessions are never part of placement changes. */
public final class WindowController {
    public enum Visibility { HIDDEN, OPENING, VISIBLE, CLOSING }

    private final Stage stage;
    private final AppPreferences preferences;
    private final ReadOnlyBooleanWrapper docked = new ReadOnlyBooleanWrapper(false);
    private final Popup snapHint = new Popup();
    private final ListChangeListener<Screen> screensChanged = change -> { ensureVisible(); persist(); refreshPill(); };
    private Visibility visibility = Visibility.HIDDEN;
    private double manualWidth = 860, manualHeight = 700;
    private Rectangle2D monitor;
    private double pressX, pressY, startX, startY, startWidth, startHeight;
    private String resizeEdge;
    private boolean moving, moved;
    private final Timeline reveal = new Timeline();
    private final SimpleDoubleProperty revealY = new SimpleDoubleProperty();
    private double settledY;
    private Stage pill;
    private Runnable onReveal;
    private boolean disposed;
    private final PauseTransition edgeHover = new PauseTransition(Duration.millis(300));
    private final PauseTransition pillDismissal = new PauseTransition(Duration.millis(180));
    private final Timeline pillAnimation = new Timeline();
    private boolean pillPreview, pillHiding;

    public WindowController(Stage stage, AppPreferences preferences) {
        this.stage = stage;
        this.preferences = preferences;
        revealY.addListener((o, before, y) -> stage.setY(y.doubleValue()));
        var saved = preferences.getWindowPlacement();
        monitor = Screen.getPrimary().getVisualBounds();
        if (saved != null && finite(saved.x(), saved.y(), saved.width(), saved.height(),
                saved.manualWidth(), saved.manualHeight(), saved.monitorX(), saved.monitorY())
                && saved.width() > 0 && saved.height() > 0) {
            monitor = Screen.getScreens().stream().map(Screen::getVisualBounds)
                    .filter(b -> b.getMinX() == saved.monitorX() && b.getMinY() == saved.monitorY())
                    .findFirst().orElse(monitor);
            docked.set(saved.docked());
            manualWidth = Math.max(420, saved.manualWidth());
            manualHeight = Math.max(320, saved.manualHeight());
            apply(saved.x(), saved.y(), saved.width(), saved.height(), monitor);
        } else {
            apply(monitor.getMinX() + (monitor.getWidth() - manualWidth) / 2,
                    monitor.getMinY() + (monitor.getHeight() - manualHeight) / 2, manualWidth, manualHeight, monitor);
        }
        var label = new Label("Release to dock at the top");
        label.setStyle("-fx-background-color:#2d2e33;-fx-text-fill:#eeeef3;-fx-font-size:13px;-fx-padding:9 18;"
                + "-fx-background-radius:18;-fx-border-radius:18;-fx-border-color:#777783;");
        snapHint.getContent().add(label);
        snapHint.setAutoFix(false);
        Screen.getScreens().addListener(screensChanged);
        stage.showingProperty().addListener((o, before, shown) -> {
            visibility = shown ? Visibility.VISIBLE : Visibility.HIDDEN;
            if (!shown) cancelInteraction();
            if (shown) {
                edgeHover.stop();
                hidePillImmediately();
                if (!preferences.isReducedMotion() && isDocked()) {
                    settledY = stage.getY(); revealY.set(settledY - 12);
                    reveal.getKeyFrames().setAll(new KeyFrame(Duration.millis(120),
                            new KeyValue(revealY, settledY, Interpolator.EASE_OUT)));
                    visibility = Visibility.OPENING;
                    reveal.setOnFinished(e -> visibility = Visibility.VISIBLE);
                    reveal.playFromStart();
                }
            } else refreshPill();
        });
    }

    public ReadOnlyBooleanProperty dockedProperty() { return docked.getReadOnlyProperty(); }
    public boolean isDocked() { return docked.get(); }
    public Visibility getVisibility() { return visibility; }
    public void opening() { ensureVisible(); visibility = stage.isShowing() ? Visibility.VISIBLE : Visibility.OPENING; }
    public void closing() { visibility = Visibility.CLOSING; settleReveal(); cancelInteraction(); persist(); }
    public void setOnReveal(Runnable action) { onReveal = action; }

    public void handleHiddenPointer(double x, double y) {
        if (disposed || stage.isShowing()) { edgeHover.stop(); return; }
        if (!preferences.isTopPill()) {
            edgeHover.stop();
            if (nearPill(x, y, pill != null && pill.isShowing())) {
                pillDismissal.stop(); showPill(true);
            } else schedulePillHide();
            return;
        }
        if (!preferences.isTopEdgeReveal()) { edgeHover.stop(); return; }
        if (!nearPill(x, y, false)) { edgeHover.stop(); return; }
        if (edgeHover.getStatus() != Timeline.Status.RUNNING) {
            edgeHover.setOnFinished(e -> {
                var current = new Robot().getMousePosition();
                if (!disposed && !stage.isShowing() && preferences.isTopPill() && preferences.isTopEdgeReveal()
                        && nearPill(current.getX(), current.getY(), false) && onReveal != null) onReveal.run();
            });
            edgeHover.playFromStart();
        }
    }

    private boolean nearPill(double x, double y, boolean includePill) {
        var screen = Screen.getScreens().stream().filter(s -> s.getVisualBounds().equals(monitor)).findFirst().orElse(Screen.getPrimary());
        var bounds = screen.getBounds();
        double center = monitor.getMinX() + monitor.getWidth() / 2;
        double bottom = includePill ? monitor.getMinY() + 36 : bounds.getMinY() + 10;
        return Math.abs(x - center) <= 110 && y >= bounds.getMinY() && y <= bottom;
    }

    public void refreshPill() {
        if (disposed || stage.isShowing() || !preferences.isTopPill()) {
            hidePillImmediately();
            return;
        }
        showPill(false);
    }

    private void showPill(boolean preview) {
        if (pill == null) {
            pill = new Stage(StageStyle.TRANSPARENT);
            pill.setAlwaysOnTop(true); pill.setTitle("Spark quick access " + UUID.randomUUID());
            var button = new Button("Spark"); button.setAccessibleText("Open Spark");
            button.setStyle("-fx-background-color:#25262a;-fx-text-fill:#dedee5;-fx-background-radius:0 0 10 10;"
                    + "-fx-padding:4 22;-fx-font-size:11;-fx-cursor:hand;");
            button.setOnAction(e -> { hidePillImmediately(); if (onReveal != null) onReveal.run(); });
            button.setOnMouseEntered(e -> { pillDismissal.stop(); if (pillHiding) showPill(true); });
            button.setOnMouseExited(e -> { if (!preferences.isTopPill()) schedulePillHide(); });
            pill.setScene(new Scene(button, 100, 26, Color.TRANSPARENT));
        }
        pill.setX(monitor.getMinX() + (monitor.getWidth() - 100) / 2); pill.setY(monitor.getMinY());
        boolean animate = !pill.isShowing() || pillHiding;
        pillPreview = preview; pillHiding = false;
        var root = pill.getScene().getRoot();
        long foreground = NativeWindowUtils.foregroundWindow();
        if (!pill.isShowing()) {
            root.setTranslateY(preview ? -26 : 0); root.setOpacity(preview ? 0 : 1);
            pill.show();
            NativeWindowUtils.makeNonActivatingToolWindow(pill.getTitle());
            NativeWindowUtils.restoreForeground(foreground);
        }
        if (!preview) { pillAnimation.stop(); root.setTranslateY(0); root.setOpacity(1); }
        else if (animate) animatePill(true);
    }

    private void schedulePillHide() {
        if (!pillPreview || pill == null || !pill.isShowing() || pillHiding || pillDismissal.getStatus() == Timeline.Status.RUNNING) return;
        pillDismissal.setOnFinished(e -> {
            var point = new Robot().getMousePosition();
            if (!preferences.isTopPill() && !nearPill(point.getX(), point.getY(), true)) animatePill(false);
        });
        pillDismissal.playFromStart();
    }

    private void animatePill(boolean show) {
        pillAnimation.stop(); pillHiding = !show;
        var root = pill.getScene().getRoot();
        double offset = show ? 0 : -26, opacity = show ? 1 : 0;
        if (preferences.isReducedMotion()) {
            root.setTranslateY(offset); root.setOpacity(opacity);
            if (!show) { pill.hide(); pillPreview = false; pillHiding = false; }
            return;
        }
        pillAnimation.getKeyFrames().setAll(new KeyFrame(Duration.millis(150),
                new KeyValue(root.translateYProperty(), offset, Interpolator.EASE_OUT),
                new KeyValue(root.opacityProperty(), opacity, Interpolator.EASE_OUT)));
        pillAnimation.setOnFinished(e -> { if (!show) { pill.hide(); pillPreview = false; pillHiding = false; } });
        pillAnimation.playFromStart();
    }

    private void hidePillImmediately() {
        pillDismissal.stop(); pillAnimation.stop(); pillPreview = false; pillHiding = false;
        if (pill != null) pill.hide();
    }

    private void settleReveal() {
        if (reveal.getStatus() == Timeline.Status.RUNNING) { reveal.stop(); stage.setY(settledY); }
    }

    public void installDragArea(Node area) {
        area.setCursor(Cursor.OPEN_HAND);
        area.setOnMousePressed(e -> {
            if (e.getButton() != MouseButton.PRIMARY) return;
            capture(e);
            moving = true;
            moved = false;
            e.consume();
        });
        area.setOnMouseDragged(e -> {
            if (!moving || !e.isPrimaryButtonDown()) return;
            double dx = e.getScreenX() - pressX, dy = e.getScreenY() - pressY;
            if (!moved && Math.hypot(dx, dy) < 5) return;
            moved = true;
            docked.set(false);
            stage.setX(startX + dx);
            stage.setY(startY + dy);
            monitor = screenAt(e.getScreenX(), e.getScreenY());
            if (nearTop(e)) {
                if (!snapHint.isShowing()) snapHint.show(stage);
                snapHint.setX(monitor.getMinX() + (monitor.getWidth() - snapHint.getWidth()) / 2);
                snapHint.setY(monitor.getMinY() + 8);
            } else snapHint.hide();
            e.consume();
        });
        area.setOnMouseReleased(e -> {
            if (!moving) return;
            boolean snap = moved && nearTop(e);
            cancelInteraction();
            if (snap) dockTo(monitor); else { ensureVisible(); persist(); }
            e.consume();
        });
        area.setOnMouseClicked(e -> { if (e.getClickCount() == 2) dock(); });
    }

    public void installResize(Scene scene, Node heightGrip) {
        scene.addEventFilter(MouseEvent.MOUSE_MOVED, e -> {
            if (!moving && resizeEdge == null) scene.setCursor(cursor(edgeAt(e)));
        });
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (e.getButton() != MouseButton.PRIMARY) return;
            var edge = edgeAt(e);
            if (edge.isEmpty()) return;
            capture(e);
            resizeEdge = edge;
            e.consume();
        });
        scene.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            if (resizeEdge == null) return;
            resize(e);
            e.consume();
        });
        scene.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            if (resizeEdge == null) return;
            manualWidth = stage.getWidth();
            manualHeight = stage.getHeight();
            cancelInteraction();
            persist();
            e.consume();
        });
        heightGrip.setCursor(Cursor.S_RESIZE);
        heightGrip.setOnMousePressed(e -> {
            if (e.getButton() == MouseButton.PRIMARY) { capture(e); resizeEdge = "s"; e.consume(); }
        });
    }

    private void capture(MouseEvent e) {
        settleReveal();
        pressX = e.getScreenX(); pressY = e.getScreenY();
        startX = stage.getX(); startY = stage.getY();
        startWidth = stage.getWidth(); startHeight = stage.getHeight();
        monitor = screenForWindow();
    }

    private void resize(MouseEvent e) {
        double dx = e.getScreenX() - pressX, dy = e.getScreenY() - pressY;
        double x = startX, y = startY, w = startWidth, h = startHeight;
        double minW = Math.min(420, monitor.getWidth()), minH = Math.min(320, monitor.getHeight());
        if (resizeEdge.contains("e")) w += dx * (isDocked() ? 2 : 1);
        if (resizeEdge.contains("w")) w -= dx * (isDocked() ? 2 : 1);
        w = Math.clamp(w, minW, isDocked() ? monitor.getWidth() :
                resizeEdge.contains("w") ? startX + startWidth - monitor.getMinX() : monitor.getMaxX() - startX);
        if (resizeEdge.contains("w") && !isDocked()) x = startX + startWidth - w;
        if (resizeEdge.contains("s")) h += dy;
        if (resizeEdge.contains("n")) h -= dy;
        h = Math.clamp(h, minH, resizeEdge.contains("n") ? startY + startHeight - monitor.getMinY() : monitor.getMaxY() - startY);
        if (resizeEdge.contains("n")) y = startY + startHeight - h;
        apply(x, y, w, h, monitor);
    }

    private String edgeAt(MouseEvent e) {
        double x = e.getSceneX(), y = e.getSceneY(), w = stage.getWidth(), h = stage.getHeight();
        boolean bottom = y >= h - 12, top = !isDocked() && y <= 5;
        boolean left = x <= (top || bottom ? 12 : 5), right = x >= w - (top || bottom ? 12 : 5);
        return (top ? "n" : bottom ? "s" : "") + (left ? "w" : right ? "e" : "");
    }

    private static Cursor cursor(String edge) {
        return switch (edge) {
            case "n", "s" -> Cursor.S_RESIZE;
            case "e", "w" -> Cursor.E_RESIZE;
            case "nw", "se" -> Cursor.NW_RESIZE;
            case "ne", "sw" -> Cursor.NE_RESIZE;
            default -> Cursor.DEFAULT;
        };
    }

    public void dock() {
        settleReveal();
        dockTo(screenForWindow());
    }

    public void toggleDock() {
        if (!isDocked()) { dock(); return; }
        settleReveal(); docked.set(false);
        apply(stage.getX(), monitor.getMinY() + 24, stage.getWidth(), stage.getHeight(), monitor);
        persist();
    }

    private void dockTo(Rectangle2D target) {
        monitor = target;
        docked.set(true);
        apply(stage.getX(), monitor.getMinY(), stage.getWidth(), stage.getHeight(), monitor);
        persist();
    }

    public void preset(double width, double height) {
        settleReveal();
        monitor = screenForWindow();
        apply(stage.getX() + (stage.getWidth() - width) / 2, stage.getY(), width, height, monitor);
        persist();
    }

    public void fill() {
        settleReveal();
        monitor = screenForWindow();
        apply(monitor.getMinX(), monitor.getMinY(), monitor.getWidth(), monitor.getHeight(), monitor);
        persist();
    }

    public void restoreManualSize() { preset(manualWidth, manualHeight); }

    public void ensureVisible() {
        settleReveal();
        monitor = screenForWindow();
        apply(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight(), monitor);
    }

    private void apply(double x, double y, double width, double height, Rectangle2D bounds) {
        width = Math.clamp(width, Math.min(420, bounds.getWidth()), bounds.getWidth());
        height = Math.clamp(height, Math.min(320, bounds.getHeight()), bounds.getHeight());
        stage.setWidth(width); stage.setHeight(height);
        stage.setX(isDocked() ? bounds.getMinX() + (bounds.getWidth() - width) / 2 : Math.clamp(x, bounds.getMinX(), bounds.getMaxX() - width));
        stage.setY(isDocked() ? bounds.getMinY() : Math.clamp(y, bounds.getMinY(), bounds.getMaxY() - height));
    }

    private Rectangle2D screenForWindow() {
        return Screen.getScreensForRectangle(stage.getX(), stage.getY(), Math.max(1, stage.getWidth()), Math.max(1, stage.getHeight()))
                .stream().max((a, b) -> Double.compare(overlap(a.getVisualBounds()), overlap(b.getVisualBounds())))
                .orElse(Screen.getPrimary()).getVisualBounds();
    }

    private double overlap(Rectangle2D b) {
        return Math.max(0, Math.min(stage.getX() + stage.getWidth(), b.getMaxX()) - Math.max(stage.getX(), b.getMinX()))
                * Math.max(0, Math.min(stage.getY() + stage.getHeight(), b.getMaxY()) - Math.max(stage.getY(), b.getMinY()));
    }

    private static Rectangle2D screenAt(double x, double y) {
        return Screen.getScreensForRectangle(x, y, 1, 1).stream().findFirst().orElse(Screen.getPrimary()).getVisualBounds();
    }

    private boolean nearTop(MouseEvent e) {
        return e.getScreenY() >= monitor.getMinY() && e.getScreenY() <= monitor.getMinY() + 36;
    }

    public void cancelInteraction() { moving = false; resizeEdge = null; snapHint.hide(); }

    public void persist() {
        preferences.setWindowPlacement(new WindowPlacement(isDocked(), stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight(),
                manualWidth, manualHeight, monitor.getMinX(), monitor.getMinY()));
    }

    public void dispose() {
        disposed = true; edgeHover.stop(); hidePillImmediately(); settleReveal(); cancelInteraction(); persist();
        if (pill != null) pill.close();
        Screen.getScreens().removeListener(screensChanged);
    }

    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }
}
