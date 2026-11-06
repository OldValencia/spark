package to.sparkapp.app.ui.topbar.components;

import javafx.scene.control.Tooltip;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import to.sparkapp.app.ui.Theme;

/**
 * Explicit styling also applies when JavaFX creates a separate tooltip window.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ShellTooltip {
    public static Tooltip create(String text) {
        var tooltip = new Tooltip(text);
        tooltip.setStyle("-fx-font-family:'" + Theme.FONT_NAME +
                "';-fx-font-size:13px;-fx-background-color:#33343b;-fx-text-fill:#eeeef3;-fx-background-radius:7;-fx-padding:8" +
                " 11;");
        tooltip.setWrapText(true);
        tooltip.setMaxWidth(360);

        return tooltip;
    }
}
