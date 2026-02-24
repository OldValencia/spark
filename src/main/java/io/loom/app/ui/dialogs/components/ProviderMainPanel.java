package io.loom.app.ui.dialogs.components;

import io.loom.app.ui.Theme;
import javafx.geometry.Insets;
import javafx.scene.layout.BorderPane;

public class ProviderMainPanel extends BorderPane {

    public ProviderMainPanel() {
        this.setPadding(new Insets(20, 24, 20, 24));
        this.setStyle(
                "-fx-background-color: " + Theme.toHex(Theme.BG_BAR) + "; " +
                        "-fx-background-radius: 14;"
        );
    }
}
