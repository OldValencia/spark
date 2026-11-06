package to.sparkapp.app.ui.settings.components;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import to.sparkapp.app.ui.Theme;

/**
 * Caption above a {@link SettingsCard}, optionally with actions aligned to its right.
 */
public class SettingsSection extends HBox {

    private static final Insets PADDING = new Insets(0, 4, 0, 4);

    public SettingsSection(String title, Node... actions) {
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(12);
        setPadding(PADDING);
        setMinHeight(22);

        var titleLabel = new Label(title.toUpperCase());
        titleLabel.getStyleClass().add("section-caption");
        titleLabel.setFont(Theme.FONT_SETTINGS_SECTION);
        titleLabel.setTextFill(Theme.TEXT_TERTIARY);
        getChildren().add(titleLabel);

        if (actions.length > 0) {
            var spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            getChildren().add(spacer);
            getChildren().addAll(actions);
        }
    }
}
