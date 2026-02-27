package to.sparkapp.app.ui.dialogs.components;

import to.sparkapp.app.config.AiConfiguration;
import to.sparkapp.app.ui.Theme;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;

public class ProviderFormPanel extends VBox {

    private final TextField nameField;
    private final TextField urlField;

    public ProviderFormPanel(AiConfiguration.AiConfig provider) {
        this.setSpacing(8);

        this.getChildren().add(createLabel("Provider Name:"));
        nameField = createTextField(provider != null ? provider.name() : "");
        this.getChildren().add(nameField);

        // Custom spacer
        var spacer = new Region();
        spacer.setMinHeight(4);
        this.getChildren().add(spacer);

        this.getChildren().add(createLabel("Website URL:"));
        urlField = createTextField(provider != null ? provider.url() : "https://");
        this.getChildren().add(urlField);

        var hintLabel = new Label("Icon and color will be automatically extracted from the website");
        hintLabel.setFont(Font.font(Theme.FONT_SETTINGS.getFamily(), FontPosture.ITALIC, 11));
        hintLabel.setTextFill(Theme.TEXT_TERTIARY);
        this.getChildren().add(hintLabel);
    }

    public String getNameFieldValue() {
        return nameField.getText().trim();
    }

    public String getUrlFieldValue() {
        return urlField.getText().trim();
    }

    private Label createLabel(String text) {
        var label = new Label(text);
        label.setFont(Theme.FONT_SETTINGS);
        label.setTextFill(Theme.TEXT_SECONDARY);
        label.setPadding(new Insets(0, 0, 4, 0));
        return label;
    }

    private TextField createTextField(String text) {
        var field = new TextField(text);
        field.setFont(Theme.FONT_SETTINGS);

        // CSS to match the custom Swing look
        var bg = Theme.toHex(Theme.BG_POPUP);
        var border = Theme.toHex(Theme.BORDER);
        var textFill = Theme.toHex(Theme.TEXT_PRIMARY);

        field.setStyle("""
                    -fx-background-color: %s;
                    -fx-border-color: %s;
                    -fx-text-fill: %s;
                    -fx-padding: 8 12 8 12;
                    -fx-border-radius: 4;
                    -fx-background-radius: 4;
                """.formatted(bg, border, textFill)
        );
        return field;
    }
}
