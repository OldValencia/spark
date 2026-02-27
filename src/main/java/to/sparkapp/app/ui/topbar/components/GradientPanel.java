package to.sparkapp.app.ui.topbar.components;

import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import to.sparkapp.app.ui.Theme;

public class GradientPanel extends BorderPane {

    private Color accentColor = Theme.ACCENT;

    public GradientPanel() {
        updateStyle();
    }

    public void updateAccentColor(Color c) {
        if (c == null || c.equals(this.accentColor)) return;
        this.accentColor = c;
        updateStyle();
    }

    private void updateStyle() {
        var bgBar = Theme.toHex(Theme.BG_BAR);
        var border = Theme.toHex(Theme.BORDER);

        int r = (int) (accentColor.getRed() * 255);
        int g = (int) (accentColor.getGreen() * 255);
        int b = (int) (accentColor.getBlue() * 255);

        var gradient = String.format(
                "linear-gradient(to right, " +
                        "rgba(%d,%d,%d,0.00) 0px, " +
                        "rgba(%d,%d,%d,0.10) 30px, " +
                        "rgba(%d,%d,%d,0.26) 70px, " +
                        "rgba(%d,%d,%d,0.18) 130px, " +
                        "rgba(%d,%d,%d,0.06) 260px, " +
                        "rgba(%d,%d,%d,0.00) 420px)",
                r, g, b,
                r, g, b,
                r, g, b,
                r, g, b,
                r, g, b,
                r, g, b
        );

        var style = String.format("""
                        -fx-background-color: %s, %s;
                        -fx-background-radius: 14 14 0 0, 14 14 0 0;
                        -fx-border-color: transparent transparent %s transparent;
                        -fx-border-width: 0 0 1 0;""",
                bgBar, gradient, border
        );

        this.setStyle(style);
    }
}
