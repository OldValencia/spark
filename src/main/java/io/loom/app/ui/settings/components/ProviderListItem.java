package io.loom.app.ui.settings.components;

import io.loom.app.config.AiConfiguration;
import io.loom.app.ui.Theme;
import io.loom.app.utils.SystemUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

class ProviderListItem extends BorderPane {

    public ProviderListItem(AiConfiguration.AiConfig provider, Runnable onEdit, Runnable onDelete) {
        this.setStyle("""
                    -fx-border-color: %s;
                    -fx-border-width: 1;
                    -fx-background-color: transparent;
                """.formatted(Theme.toHex(Theme.BORDER)));
        this.setPadding(new Insets(8, 12, 8, 12));

        double itemHeight = SystemUtils.isWindows() ? 40 : 50;
        this.setMaxHeight(itemHeight);
        this.setMinHeight(itemHeight);

        this.setLeft(createColorStrip(provider.color()));

        var infoPanel = createInfoPanel(provider);
        BorderPane.setMargin(infoPanel, new Insets(0, 0, 0, 12));
        this.setCenter(infoPanel);

        this.setRight(createActionButtons(onEdit, onDelete));
    }

    private Node createColorStrip(String colorHex) {
        var rect = new Rectangle(4, 32);
        rect.setArcWidth(4);
        rect.setArcHeight(4);

        try {
            rect.setFill(Color.web(colorHex));
        } catch (Exception e) {
            rect.setFill(Theme.ACCENT);
        }

        // Wrap in a VBox to center it vertically
        var container = new VBox(rect);
        container.setAlignment(Pos.CENTER);
        return container;
    }

    private Node createInfoPanel(AiConfiguration.AiConfig provider) {
        var nameLabel = new Label(provider.name());
        nameLabel.setFont(Font.font(Theme.FONT_SETTINGS.getFamily(), FontWeight.BOLD, Theme.FONT_SETTINGS.getSize()));
        nameLabel.setTextFill(Theme.TEXT_PRIMARY);

        if (SystemUtils.isWindows()) {
            var infoPanel = new HBox();
            infoPanel.setAlignment(Pos.CENTER_LEFT);

            var urlLabel = new Label(" (" + provider.url() + ")");
            urlLabel.setFont(Font.font(Theme.FONT_SETTINGS.getFamily(), 11));
            urlLabel.setTextFill(Theme.TEXT_TERTIARY);

            infoPanel.getChildren().addAll(nameLabel, urlLabel);
            return infoPanel;
        } else {
            var infoPanel = new VBox(2);
            infoPanel.setAlignment(Pos.CENTER_LEFT);

            var urlLabel = new Label(provider.url());
            urlLabel.setFont(Font.font(Theme.FONT_SETTINGS.getFamily(), 11));
            urlLabel.setTextFill(Theme.TEXT_TERTIARY);

            infoPanel.getChildren().addAll(nameLabel, urlLabel);
            return infoPanel;
        }
    }

    private Node createActionButtons(Runnable onEdit, Runnable onDelete) {
        var actionsPanel = new HBox(8);
        actionsPanel.setAlignment(Pos.CENTER_RIGHT);

        actionsPanel.getChildren().addAll(
                new ProvidersListTextButton("Edit", Theme.TEXT_SECONDARY, onEdit),
                new ProvidersListTextButton("Delete", Theme.TEXT_SECONDARY, onDelete)
        );

        return actionsPanel;
    }
}
