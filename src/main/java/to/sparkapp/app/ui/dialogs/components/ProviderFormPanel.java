package to.sparkapp.app.ui.dialogs.components;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import to.sparkapp.app.config.AiConfiguration;
import to.sparkapp.app.ui.Theme;

public class ProviderFormPanel extends VBox {
    private final TextField nameField;
    private final TextField urlField;
    private final ColorPicker colorPicker;
    private final AiConfiguration.AiConfig provider;
    private boolean manualColor, useIconColor;

    public ProviderFormPanel(AiConfiguration.AiConfig provider) {
        this.provider = provider;
        setSpacing(14);
        nameField = new TextField(provider == null ? "" : provider.name());
        nameField.setPromptText("Site name"); nameField.setAccessibleText("Site name");
        urlField = new TextField(provider == null ? "https://" : provider.url());
        urlField.setAccessibleText("Website address");
        var hint = new Label("The site icon is fetched automatically.");
        hint.getStyleClass().add("field-hint"); hint.setWrapText(true);
        Color initial = Color.web("#bcbcc6");
        if (provider != null) { try { initial = Color.web(provider.color()); } catch (RuntimeException ignored) {} }
        colorPicker = new ColorPicker(initial); colorPicker.setAccessibleText("Site accent color");
        colorPicker.setOnAction(e -> { manualColor = true; useIconColor = false; hint.setText("Your color will be kept for this site."); });
        var automatic = new Button("Use icon color"); automatic.getStyleClass().add("quiet-button");
        automatic.setOnAction(e -> { useIconColor = true; manualColor = false; hint.setText("The accent will be taken from the icon when saved."); });
        getChildren().addAll(field("Name", nameField), field("Website", urlField), field("Accent", new HBox(8, colorPicker, automatic)), hint);
    }

    private VBox field(String title, Node control) {
        var label = new Label(title); label.getStyleClass().add("field-label");
        return new VBox(6, label, control);
    }

    public String getColorValue() {
        if (useIconColor) return null;
        return manualColor ? Theme.toHex(colorPicker.getValue()) : provider == null ? null : provider.color();
    }
    public boolean isUseIconColor() { return useIconColor; }
    public boolean isManualColor() { return manualColor; }
    public String getNameFieldValue() { return nameField.getText().trim(); }
    public String getUrlFieldValue() { return urlField.getText().trim(); }
}
