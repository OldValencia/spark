package to.sparkapp.app.ui.settings.components;

import to.sparkapp.app.ui.Theme;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.text.Font;

class ProvidersEmptyListLabel extends Label {

    ProvidersEmptyListLabel() {
        super("No providers yet — use “+ Add” to create one.");
        this.setFont(Font.font(Theme.FONT_SETTINGS.getFamily(), 13));
        this.setTextFill(Theme.TEXT_TERTIARY);
        this.setPadding(new Insets(24, 12, 24, 12));
        this.setMaxWidth(Double.MAX_VALUE);
        this.setAlignment(Pos.CENTER);
    }
}
