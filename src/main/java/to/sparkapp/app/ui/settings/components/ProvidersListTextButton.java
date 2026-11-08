package to.sparkapp.app.ui.settings.components;

import to.sparkapp.app.ui.Theme;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

/**
 * Borderless text action used in the providers list ("Edit", "Delete", "+ Add").
 */
class ProvidersListTextButton extends Label {

    ProvidersListTextButton(String text, Color defaultColor, Color hoverColor, Runnable action) {
        super(text);
        this.setFont(Font.font(Theme.FONT_SETTINGS.getFamily(), 13));
        this.setTextFill(defaultColor);
        this.setCursor(Cursor.HAND);

        this.setOnMouseClicked(e -> {
            if (action != null) {
                action.run();
            }
        });

        this.setOnMouseEntered(e -> this.setTextFill(hoverColor));
        this.setOnMouseExited(e -> this.setTextFill(defaultColor));
    }
}
