package to.sparkapp.app.ui.settings.components;

import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import lombok.Setter;

@Setter
public class ClearCookiesButton extends HBox {

    private Runnable onClearCookies;

    public ClearCookiesButton() {
        this.setAlignment(Pos.CENTER_LEFT);
        this.setMaxWidth(Double.MAX_VALUE);

        var clearCookiesBtn = new AnimatedSettingsButton("Clear cookies", () -> {
            if (onClearCookies != null) {
                onClearCookies.run();
            }
        });

        this.getChildren().add(clearCookiesBtn);
    }
}
