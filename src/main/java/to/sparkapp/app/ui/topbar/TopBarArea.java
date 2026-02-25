package to.sparkapp.app.ui.topbar;

import to.sparkapp.app.config.AiConfiguration;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.ui.webview.FxWebViewPane;
import to.sparkapp.app.ui.topbar.components.GradientPanel;
import to.sparkapp.app.windows.SettingsWindow;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

public class TopBarArea extends GradientPanel {

    private final Stage frame;
    private final SettingsWindow settingsWindow;

    private double initialX;
    private double initialY;

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
            initialX = e.getSceneX();
            initialY = e.getSceneY();

            if (settingsWindow != null && settingsWindow.isOpen()) {
                settingsWindow.close();
            }
        });

        this.setOnMouseDragged(e -> {
            frame.setX(e.getScreenX() - initialX);
            frame.setY(e.getScreenY() - initialY);
        });
    }
}
