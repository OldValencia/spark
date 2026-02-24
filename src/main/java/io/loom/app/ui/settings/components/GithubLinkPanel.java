package io.loom.app.ui.settings.components;

import io.loom.app.ui.Theme;
import io.loom.app.utils.UrlUtils;
import io.loom.app.utils.SystemUtils;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.text.Font;

public class GithubLinkPanel extends HBox {

    private static final String GITHUB_URL = "https://github.com/oldvalencia/loom";
    private static final String VERSION_TEMPLATE = "Loom application on Github (v %s)";

    public GithubLinkPanel() {
        this.setAlignment(Pos.CENTER);
        this.setMaxWidth(Double.MAX_VALUE);

        this.getChildren().add(buildGithubLabel());
    }

    private Label buildGithubLabel() {
        var text = String.format(VERSION_TEMPLATE, SystemUtils.VERSION);
        var label = new Label(text);
        label.setFont(Font.font(Theme.FONT_SETTINGS.getFamily(), 11));
        label.setTextFill(Theme.TEXT_TERTIARY);
        label.setCursor(Cursor.HAND);

        label.setOnMouseClicked(e -> {
            label.setTextFill(Theme.TEXT_TERTIARY);
            UrlUtils.openLink(GITHUB_URL);
        });

        label.setOnMouseEntered(e -> label.setTextFill(Theme.TEXT_SECONDARY));
        label.setOnMouseExited(e -> label.setTextFill(Theme.TEXT_TERTIARY));

        return label;
    }
}
