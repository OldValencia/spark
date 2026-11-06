package to.sparkapp.app.ui.dialogs.components;

import javafx.geometry.Insets;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;

public class ProviderMainPanel extends BorderPane {

    /**
     * Space the dialog scene has to reserve around the panel for the shadow.
     */
    public static final double SHADOW_PADDING = 20;

    public ProviderMainPanel() {
        this.setPadding(new Insets(22));
        getStyleClass().add("shell-dialog");

        var shadow = new DropShadow();
        shadow.setRadius(14);
        shadow.setSpread(0);
        shadow.setOffsetY(5);
        shadow.setColor(Color.rgb(0, 0, 0, 0.28));
        this.setEffect(shadow);
    }
}
