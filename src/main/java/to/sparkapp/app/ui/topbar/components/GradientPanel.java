package to.sparkapp.app.ui.topbar.components;

import to.sparkapp.app.ui.Theme;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;

public class GradientPanel extends BorderPane {

    private Color accentColor = Theme.ACCENT;

    public GradientPanel() {
        updateStyle();
    }

    public void updateAccentColor(Color c) {
        if (c == null || c.equals(this.accentColor)) {
            return;
        }
        this.accentColor = c;
        updateStyle();
    }

    private void updateStyle() {
        var bgBar = Theme.toHex(Theme.BG_BAR);
        var border = Theme.toHex(Theme.BORDER);
        var r = (int) (accentColor.getRed() * 255);
        var g = (int) (accentColor.getGreen() * 255);
        var b = (int) (accentColor.getBlue() * 255);
        var transparent = String.format("rgba(%d, %d, %d, 0.0)", r, g, b);
        var faded = String.format("rgba(%d, %d, %d, 0.15)", r, g, b);
        var gradient = String.format(
                "linear-gradient(to right, %s 0px, %s 50px, %s 20%%, transparent 20.01%%)",
                transparent, faded, transparent
        );
        var style = String.format(
                """
                        -fx-background-color: %s, %s;
                        -fx-background-radius: 14 14 0 0, 14 14 0 0;
                        -fx-border-color: transparent transparent %s transparent;
                        -fx-border-width: 0 0 1 0;""",
                bgBar, gradient, border
        );

        this.setStyle(style);
    }
}
