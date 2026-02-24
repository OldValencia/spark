package to.sparkapp.app.ui.topbar;

import to.sparkapp.app.config.AiConfiguration;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.ui.FxWebViewPane;
import to.sparkapp.app.ui.topbar.components.AiDock;
import javafx.geometry.Insets;
import javafx.scene.layout.HBox;

class LeftTopBarArea extends HBox {

    public LeftTopBarArea(AiConfiguration aiConfiguration, FxWebViewPane fxWebViewPane, AppPreferences appPreferences) {
        this.setStyle("-fx-background-color: transparent;");
        this.setPadding(new Insets(0, 0, 0, 15));
        this.getChildren().add(new AiDock(aiConfiguration.getConfigurations(), fxWebViewPane, appPreferences));
    }
}
