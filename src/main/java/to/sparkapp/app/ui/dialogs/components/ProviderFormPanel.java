package to.sparkapp.app.ui.dialogs.components;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import lombok.Getter;
import to.sparkapp.app.config.AiConfiguration.AiConfig;
import to.sparkapp.app.ui.SiteAccent;
import to.sparkapp.app.ui.Theme;

public class ProviderFormPanel extends VBox {
    private final TextField nameField;
    private final TextField urlField;
    private final ColorPicker colorPicker;
    private final Label colorHint = new Label("The site icon is fetched automatically.");
    private final AiConfig provider;
    @Getter
    private boolean manualColor;
    @Getter
    private boolean useIconColor;

    public ProviderFormPanel(AiConfig provider) {
        this.provider = provider;
        setSpacing(14);
        nameField = createTextField(provider == null ? "" : provider.name(), "Site name");
        nameField.setPromptText("Site name");
        urlField = createTextField(provider == null ? "https://" : provider.url(), "Website address");
        colorPicker = new ColorPicker(initialColor());
        colorPicker.setAccessibleText("Site accent color");
        colorPicker.setOnAction(event -> selectManualColor());

        var automatic = new Button("Use icon color");
        automatic.getStyleClass().add("quiet-button");
        automatic.setOnAction(event -> selectIconColor());
        colorHint.getStyleClass().add("field-hint");
        colorHint.setWrapText(true);
        getChildren().addAll(field("Name", nameField), field("Website", urlField),
                field("Accent", new HBox(8, colorPicker, automatic)), colorHint);
    }

    private TextField createTextField(String value, String accessibleText) {
        var field = new TextField(value);
        field.setAccessibleText(accessibleText);

        return field;
    }

    private Color initialColor() {
        return provider == null ? SiteAccent.DEFAULT : SiteAccent.resolve(provider.color());
    }

    private void selectManualColor() {
        manualColor = true;
        useIconColor = false;
        colorHint.setText("Your color will be kept for this site.");
    }

    private void selectIconColor() {
        useIconColor = true;
        manualColor = false;
        colorHint.setText("The accent will be taken from the icon when saved.");
    }

    private VBox field(String title, Node control) {
        var label = new Label(title);
        label.getStyleClass().add("field-label");

        return new VBox(6, label, control);
    }

    public String getColorValue() {
        if (useIconColor) {
            return null;
        }

        if (manualColor) {
            return Theme.toHex(colorPicker.getValue());
        }

        return provider == null ? null : provider.color();
    }

    public String getNameFieldValue() {
        return nameField.getText().trim();
    }

    public String getUrlFieldValue() {
        return urlField.getText().trim();
    }
}
