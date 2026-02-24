package io.loom.app.ui.topbar;

import io.loom.app.ui.FxWebViewPane;
import io.loom.app.ui.Theme;
import io.loom.app.ui.topbar.components.AnimatedIconButton;
import io.loom.app.ui.topbar.components.ZoomButton;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;

class RightTopBarArea extends HBox {

    public RightTopBarArea(FxWebViewPane fxWebViewPane, Runnable onSettingsToggle, Runnable onCloseWindow) {
        this.setAlignment(Pos.CENTER_RIGHT);
        this.setSpacing(6);
        this.setPadding(new Insets(0, 10, 0, 0));
        this.setStyle("-fx-background-color: transparent;");

        var zoomButton = new ZoomButton(fxWebViewPane::resetZoom);
        fxWebViewPane.setZoomCallback(zoomButton::updateZoomDisplay);
        HBox.setMargin(zoomButton, new Insets(0, 9, 0, 0));

        var settingsButton = new AnimatedIconButton("⚙", Theme.BTN_HOVER_SETTINGS, onSettingsToggle);
        var closeButton = new AnimatedIconButton("✕", Theme.BTN_HOVER_CLOSE, onCloseWindow);

        this.getChildren().addAll(zoomButton, settingsButton, closeButton);
    }
}
