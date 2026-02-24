package io.loom.app.ui.topbar;

import io.loom.app.config.AiConfiguration;
import io.loom.app.config.AppPreferences;
import io.loom.app.ui.FxWebViewPane;
import io.loom.app.ui.topbar.components.AiDock;
import javafx.geometry.Insets;
import javafx.scene.layout.HBox;

class LeftTopBarArea extends HBox {

    public LeftTopBarArea(AiConfiguration aiConfiguration, FxWebViewPane fxWebViewPane, AppPreferences appPreferences) {
        this.setStyle("-fx-background-color: transparent;");
        this.setPadding(new Insets(0, 0, 0, 15));
        this.getChildren().add(new AiDock(aiConfiguration.getConfigurations(), fxWebViewPane, appPreferences));
    }
}
