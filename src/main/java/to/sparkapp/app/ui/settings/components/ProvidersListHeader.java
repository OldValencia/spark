package to.sparkapp.app.ui.settings.components;

import to.sparkapp.app.ui.Theme;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

class ProvidersListHeader extends HBox {

    ProvidersListHeader(Runnable action) {
        this.setAlignment(Pos.CENTER_LEFT);

        var titleLabel = new Label("AI PROVIDERS");
        titleLabel.setFont(Theme.FONT_SETTINGS_SECTION);
        titleLabel.setTextFill(Theme.TEXT_TERTIARY);

        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        var addButton = new ProvidersListTextButton("+ Add", Theme.ACCENT, action);

        this.getChildren().addAll(titleLabel, spacer, addButton);
    }
}
