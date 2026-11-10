package to.sparkapp.app.windows;

import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Popup;
import javafx.stage.Stage;

final class WindowPointerInteraction {
    private final Stage stage;
    private final WindowGeometry geometry;
    private final Runnable settleReveal;
    private final Popup snapHint = new Popup();

    private WindowDragOrigin origin;
    private String resizeEdge;
    private boolean moving;
    private boolean moved;

    WindowPointerInteraction(Stage stage, WindowGeometry geometry, Runnable settleReveal) {
        this.stage = stage;
        this.geometry = geometry;
        this.settleReveal = settleReveal;

        var label = new Label("Release to dock at the top");
        label.setStyle("-fx-background-color:#2d2e33;-fx-text-fill:#eeeef3;-fx-font-size:13px;-fx-padding:9" +
                " 18;-fx-background-radius:18;-fx-border-radius:18;-fx-border-color:#777783;");
        snapHint.getContent().add(label);
        snapHint.setAutoFix(false);
    }

    private boolean nearTop(MouseEvent event) {
        return event.getScreenY() >= geometry.monitor.getMinY() &&
                event.getScreenY() <= geometry.monitor.getMinY() + 36;
    }

    void cancel() {
        moving = false;
        resizeEdge = null;
        snapHint.hide();
    }

    public void installDragArea(Node area) {
        area.setCursor(Cursor.OPEN_HAND);
        area.setOnMousePressed(e -> {
            if (e.getButton() != MouseButton.PRIMARY) {
                return;
            }

            capture(e);
            moving = true;
            moved = false;
            e.consume();
        });
        area.setOnMouseDragged(e -> {
            if (!moving || !e.isPrimaryButtonDown()) {
                return;
            }

            double dx = e.getScreenX() - origin.screenX();
            double dy = e.getScreenY() - origin.screenY();

            if (!moved && Math.hypot(dx, dy) < 5) {
                return;
            }

            moved = true;
            geometry.docked.set(false);
            stage.setX(origin.x() + dx);
            stage.setY(origin.y() + dy);
            geometry.monitor = WindowScreens.screenAt(e.getScreenX(), e.getScreenY());

            if (nearTop(e)) {
                if (!snapHint.isShowing()) {
                    snapHint.show(stage);
                }

                snapHint.setX(geometry.monitor.getMinX() + (geometry.monitor.getWidth() - snapHint.getWidth()) / 2);
                snapHint.setY(geometry.monitor.getMinY() + 8);
            } else {
                snapHint.hide();
            }

            e.consume();
        });
        area.setOnMouseReleased(e -> {
            if (!moving) {
                return;
            }

            boolean snap = moved && nearTop(e);
            cancel();

            if (snap) {
                geometry.dockTo(geometry.monitor);
            } else {
                geometry.ensureVisible();
                geometry.persist();
            }

            e.consume();
        });
        area.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                geometry.dock();
            }
        });
    }

    public void installResize(Scene scene, Node heightGrip) {
        scene.addEventFilter(MouseEvent.MOUSE_MOVED, e -> {
            if (!moving && resizeEdge == null) {
                scene.setCursor(cursor(edgeAt(e)));
            }
        });
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (e.getButton() != MouseButton.PRIMARY) {
                return;
            }

            var edge = edgeAt(e);

            if (edge.isEmpty()) {
                return;
            }

            capture(e);
            resizeEdge = edge;
            e.consume();
        });
        scene.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            if (resizeEdge == null) {
                return;
            }

            WindowResizeBounds.apply(origin, e, resizeEdge, geometry);
            e.consume();
        });
        scene.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            if (resizeEdge == null) {
                return;
            }

            geometry.rememberManualSize();
            cancel();
            geometry.persist();
            e.consume();
        });
        heightGrip.setCursor(Cursor.S_RESIZE);
        heightGrip.setOnMousePressed(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                capture(e);
                resizeEdge = "s";
                e.consume();
            }
        });
    }

    private void capture(MouseEvent e) {
        settleReveal.run();
        origin = WindowDragOrigin.capture(stage, e);
        geometry.monitor = geometry.screenForWindow();
    }

    private String edgeAt(MouseEvent e) {
        double x = e.getSceneX();
        double y = e.getSceneY();
        double w = stage.getWidth();
        double h = stage.getHeight();

        boolean bottom = y >= h - 12;
        boolean top = !geometry.isDocked() && y <= 5;

        boolean left = x <= (top || bottom ? 12 : 5);
        boolean right = x >= w - (top || bottom ? 12 : 5);

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
}
