package to.sparkapp.app.ui.topbar;

import to.sparkapp.app.config.AiConfiguration;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.ui.webview.FxWebViewPane;
import to.sparkapp.app.ui.topbar.components.AiDock;
import to.sparkapp.app.ui.topbar.components.AnimatedIconButton;
import to.sparkapp.app.ui.topbar.components.DockItemNode;
import to.sparkapp.app.ui.topbar.components.GradientPanel;
import to.sparkapp.app.ui.topbar.components.ZoomButton;
import to.sparkapp.app.windows.SettingsWindow;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ScrollPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

public class TopBarArea extends GradientPanel {

    private final Stage frame;
    private final SettingsWindow settingsWindow;

    private double initialX = Double.NaN;
    private double initialY = Double.NaN;

    public TopBarArea(AiConfiguration aiConfiguration,
                      FxWebViewPane fxWebViewPane,
                      Stage frame,
                      SettingsWindow settingsWindow,
                      AppPreferences appPreferences,
                      Runnable onSettingsToggle,
                      Runnable onCloseWindow) {
        super();

        this.frame = frame;
        this.settingsWindow = settingsWindow;

        this.setPrefSize(frame.getWidth(), 48);
        this.setLeft(new LeftTopBarArea(aiConfiguration, fxWebViewPane, appPreferences));
        this.setRight(new RightTopBarArea(fxWebViewPane, onSettingsToggle, onCloseWindow));

        setupDragging();

        if (!aiConfiguration.getConfigurations().isEmpty()) {
            this.updateAccentColor(Color.web(aiConfiguration.getConfigurations().getFirst().color()));
        }
    }

    private void setupDragging() {
        this.setOnMousePressed(e -> {
            if (!isDraggableTarget((Node) e.getTarget())) {
                initialX = Double.NaN;
                initialY = Double.NaN;
                return;
            }
            initialX = e.getSceneX();
            initialY = e.getSceneY();

            if (settingsWindow != null && settingsWindow.isOpen()) {
                settingsWindow.close();
            }
        });

        this.setOnMouseDragged(e -> {
            if (Double.isNaN(initialX)) return;
            frame.setX(e.getScreenX() - initialX);
            frame.setY(e.getScreenY() - initialY);
        });
    }

    private static boolean isDraggableTarget(Node node) {
        while (node != null) {
            if (node instanceof ButtonBase
                    || node instanceof DockItemNode
                    || node instanceof AnimatedIconButton
                    || node instanceof ZoomButton
                    || node instanceof ScrollPane
                    || node instanceof AiDock) {
                return false;
            }
            node = node.getParent();
        }
        return true;
    }
}
