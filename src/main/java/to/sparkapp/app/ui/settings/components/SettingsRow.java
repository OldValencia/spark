package to.sparkapp.app.ui.settings.components;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import to.sparkapp.app.ui.Theme;

/**
 * One setting inside a {@link SettingsCard}: a title, an optional explanation
 * underneath it, and the control that changes the value.
 */
public class SettingsRow extends HBox {

    private static final Insets PADDING = new Insets(11, 12, 11, 12);

    public SettingsRow(String title, String description, Node control) {
        setAlignment(Pos.CENTER_LEFT);
        setMaxWidth(Double.MAX_VALUE);
        setSpacing(16);
        setPadding(PADDING);
        setMinHeight(description == null ? 46 : 56);

        getChildren().add(buildTextBlock(title, description));

        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        getChildren().add(spacer);

        if (control != null) {
            getChildren().add(control);
        }

        applyHoverHighlight(this);
    }

    private static Node buildTextBlock(String title, String description) {
        var titleLabel = new Label(title);
        titleLabel.getStyleClass().add("setting-title");
        titleLabel.setFont(Theme.FONT_SETTINGS_TITLE);
        titleLabel.setTextFill(Theme.TEXT_PRIMARY);

        if (description == null) {
            return titleLabel;
        }

        var descriptionLabel = new Label(description);
        descriptionLabel.getStyleClass().add("setting-description");
        descriptionLabel.setFont(Theme.FONT_SETTINGS_HINT);
        descriptionLabel.setTextFill(Theme.TEXT_TERTIARY);
        descriptionLabel.setWrapText(true);

        var textBlock = new VBox(3, titleLabel, descriptionLabel);
        textBlock.setMinWidth(0); HBox.setHgrow(textBlock, Priority.ALWAYS);
        textBlock.setAlignment(Pos.CENTER_LEFT);
        return textBlock;
    }

    /**
     * Inset highlight — it never reaches the card corners, so no clipping is needed.
     */
    static void applyHoverHighlight(Region row) {
        var hovered = "-fx-background-color: %s; -fx-background-radius: %spx;"
                .formatted(Theme.toHex(Theme.BG_ROW_HOVER), Theme.ROW_RADIUS);

        row.setOnMouseEntered(e -> row.setStyle(hovered));
        row.setOnMouseExited(e -> row.setStyle(""));
    }
}
