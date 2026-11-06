package to.sparkapp.app.ui.topbar.utils;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.collections.ObservableList;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.util.Duration;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.ui.topbar.components.AiDock;
import to.sparkapp.app.ui.topbar.components.DockItemNode;

public class AiDockDragManager {
    private static final double HOLD_SCROLL_RATE = 0.010;

    private final AiDock dock;
    private final AppPreferences appPreferences;

    private Timeline autoscrollTimeline = null;
    private double lastDragSceneX = 0;
    private int dragStartIndex = -1;
    private int dragCurrentTargetIndex = -1;
    private double dragNodeWidth = 0;
    private DockItemNode draggedNode;

    public void cancel() {
        stopAutoscroll();
        draggedNode = null;
        dragStartIndex = -1;
        dragCurrentTargetIndex = -1;
        dock.getDockItems().forEach(DockItemNode::cancelDrag);
    }

    public AiDockDragManager(AiDock dock, AppPreferences appPreferences) {
        this.dock = dock;
        this.appPreferences = appPreferences;
    }

    public void startAutoscrollIfNeeded(double sceneX) {
        this.lastDragSceneX = sceneX;

        if (autoscrollTimeline == null) {
            autoscrollTimeline = new Timeline(new KeyFrame(Duration.millis(16), e -> {
                var scrollBounds = dock.getScrollPane().localToScene(dock.getScrollPane().getBoundsInLocal());

                if (scrollBounds == null) {
                    return;
                }

                var step = getStep(scrollBounds);

                if (step != 0) {
                    dock.getScrollPane().setHvalue(Math.clamp(dock.getScrollPane().getHvalue() + step, 0.0, 1.0));
                    dock.refreshArrowOpacity();

                    if (draggedNode != null && draggedNode.isDragging()) {
                        draggedNode.updateDragPosition(lastDragSceneX);
                    }
                }
            }));
            autoscrollTimeline.setCycleCount(Animation.INDEFINITE);
        }

        autoscrollTimeline.play();
    }

    private double getStep(Bounds scrollBounds) {
        double leftThreshold = scrollBounds.getMinX() + 30;
        double rightThreshold = scrollBounds.getMaxX() - 30;

        double step = 0;

        if (lastDragSceneX < leftThreshold) {
            double ratio = (leftThreshold - lastDragSceneX) / 30.0;
            step = -HOLD_SCROLL_RATE * Math.min(2.0, ratio);
        } else if (lastDragSceneX > rightThreshold) {
            double ratio = (lastDragSceneX - rightThreshold) / 30.0;
            step = HOLD_SCROLL_RATE * Math.min(2.0, ratio);
        }

        return step;
    }

    public void stopAutoscroll() {
        if (autoscrollTimeline != null) {
            autoscrollTimeline.stop();
        }
    }

    public void onDragStart(DockItemNode draggedNode) {
        this.draggedNode = draggedNode;

        var children = dock.getDockContainer().getChildren();
        dragStartIndex = children.indexOf(draggedNode);
        dragCurrentTargetIndex = dragStartIndex;
        dragNodeWidth = draggedNode.getLayoutBounds().getWidth() + dock.getDockContainer().getSpacing();

        draggedNode.setViewOrder(-10.0);
    }

    public void onDrag(double visualCenterX) {
        var children = dock.getDockContainer().getChildren();
        int insertIndex = getInsertIndex(visualCenterX, children);

        if (insertIndex != dragCurrentTargetIndex) {
            dragCurrentTargetIndex = insertIndex;

            for (int i = 0; i < children.size(); i++) {
                if (i == dragStartIndex) {
                    continue;
                }

                var child = children.get(i);
                var targetTranslateX = getTargetTranslateX(i);

                var oldAnim = (Timeline) child.getProperties().get("dragAnim");

                if (oldAnim != null) {
                    oldAnim.stop();
                }

                var anim = new Timeline(new KeyFrame(Duration.millis(appPreferences.isReducedMotion() ? 1 : 150),
                        new KeyValue(child.translateXProperty(), targetTranslateX)));
                child.getProperties().put("dragAnim", anim);
                anim.play();
            }
        }
    }

    private double getTargetTranslateX(int i) {
        double targetTranslateX = 0;

        if (dragStartIndex < dragCurrentTargetIndex) {
            if (i > dragStartIndex && i <= dragCurrentTargetIndex) {
                targetTranslateX = -dragNodeWidth;
            }
        } else if (dragStartIndex > dragCurrentTargetIndex) {
            if (i >= dragCurrentTargetIndex && i < dragStartIndex) {
                targetTranslateX = dragNodeWidth;
            }
        }

        return targetTranslateX;
    }

    private int getInsertIndex(double visualCenterX, ObservableList<Node> children) {
        int newTargetIndex = dragStartIndex;

        for (int i = 0; i < children.size(); i++) {
            var child = children.get(i);
            double centerX = child.getLayoutX() + child.getLayoutBounds().getWidth() / 2.0;

            if (visualCenterX < centerX) {
                newTargetIndex = i;
                break;
            } else if (i == children.size() - 1) {
                newTargetIndex = children.size();
            }
        }

        int insertIndex;

        if (newTargetIndex > dragStartIndex) {
            insertIndex = newTargetIndex - 1;
        } else {
            insertIndex = newTargetIndex;
        }

        return insertIndex;
    }

    public void onDragEnd(DockItemNode draggedNode) {
        draggedNode.setViewOrder(0.0);

        var children = dock.getDockContainer().getChildren();

        if (dragCurrentTargetIndex != dragStartIndex && dragCurrentTargetIndex >= 0) {
            double layoutXBefore = draggedNode.getLayoutX();

            children.remove(draggedNode);
            children.add(dragCurrentTargetIndex, draggedNode);

            dock.getDockContainer().layout();

            double layoutXAfter = draggedNode.getLayoutX();
            double diff = layoutXAfter - layoutXBefore;

            draggedNode.setTranslateX(draggedNode.getTranslateX() - diff);

            dock.getDockItems().clear();

            for (var child : children) {
                if (child instanceof DockItemNode din) {
                    dock.getDockItems().add(din);
                }
            }

            AiDockOrderUtils.saveCurrentOrder(dock.getDockItems(), appPreferences);
            dock.orderChanged();
        }

        for (var child : children) {
            if (child != draggedNode) {
                Timeline oldAnim = (Timeline) child.getProperties().get("dragAnim");

                if (oldAnim != null) {
                    oldAnim.stop();
                }

                child.setTranslateX(0);
            }
        }

        dragStartIndex = -1;
        dragCurrentTargetIndex = -1;
        this.draggedNode = null;
        dock.getDockItems().forEach(DockItemNode::refreshState);
    }
}
