package to.sparkapp.app.ui.dialogs.components;

import to.sparkapp.app.ui.Theme;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public class ProviderTitleLabel extends Label {

    public ProviderTitleLabel(boolean isAddDialog) {
        super(isAddDialog ? "Add New AI Provider" : "Edit AI Provider");
        this.setFont(Font.font(Theme.FONT_SETTINGS.getFamily(), FontWeight.BOLD, 18));
        this.setTextFill(Theme.TEXT_PRIMARY);
        this.setPadding(new Insets(0, 0, 16, 0));
    }
}
