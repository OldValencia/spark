package to.sparkapp.app.ui.settings.components;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import to.sparkapp.app.ui.topbar.components.ShellIcon;
import to.sparkapp.app.ui.topbar.components.ShellTooltip;
import to.sparkapp.app.utils.SystemUtils;
import to.sparkapp.app.utils.UrlUtils;

/**
 * Project information and support stay together, with distinct visual weights.
 */
public class DonationSection extends GridPane {
    public DonationSection() {
        setHgap(10);
        setVgap(10);
        setMaxWidth(Double.MAX_VALUE);

        var project = card("project-card");
        var brand = new Label("Spark");
        brand.getStyleClass().add("project-title");

        var version = new Label("v" + SystemUtils.VERSION);
        version.getStyleClass().add("project-version");

        var space = new Region();
        HBox.setHgrow(space, Priority.ALWAYS);

        var heading = new HBox(8, brand, space, version);
        heading.setAlignment(Pos.CENTER_LEFT);

        var description = caption("Your favorite sites, always within reach.");
        var github = new Button();
        github.getStyleClass().add("project-link");
        github.setMaxWidth(Double.MAX_VALUE);

        var githubSpace = new Region();
        HBox.setHgrow(githubSpace, Priority.ALWAYS);

        var githubContent = new HBox(8, ShellIcon.create("code-bracket"), new Label("GitHub"), githubSpace,
                ShellIcon.create("arrow-up-right"));
        githubContent.setAlignment(Pos.CENTER_LEFT);
        githubContent.prefWidthProperty().bind(github.widthProperty().subtract(20));
        github.setGraphic(githubContent);
        github.setAccessibleText("Spark on GitHub");
        github.setTooltip(ShellTooltip.create("Source code, releases and issues"));
        github.setOnAction(e -> UrlUtils.openLink("https://github.com/oldvalencia/spark"));

        var projectGap = new Region();
        VBox.setVgrow(projectGap, Priority.ALWAYS);
        project.getChildren().addAll(heading, description, projectGap, github);

        var support = card("support-card");
        var title = new Label("Support Spark", ShellIcon.create("heart"));
        title.setGraphicTextGap(8);
        title.getStyleClass().add("support-title");

        var supportGap = new Region();
        VBox.setVgrow(supportGap, Priority.ALWAYS);

        var coffee = supportLink("Coffee", "coffee", "Buy me a coffee", "https://buymeacoffee.com/oldvalencia");
        var kofi = supportLink("Ko-fi", "gift", "Support on Ko-fi", "https://ko-fi.com/oldvalencia");
        var links = new HBox(6, coffee, kofi);
        HBox.setHgrow(coffee, Priority.ALWAYS);
        HBox.setHgrow(kofi, Priority.ALWAYS);
        support.getChildren().addAll(title, caption("Support the next Spark release."), supportGap, links);

        var first = new ColumnConstraints();
        var second = new ColumnConstraints();
        first.setPercentWidth(50);
        second.setPercentWidth(50);
        getColumnConstraints().addAll(first, second);
        add(project, 0, 0);
        add(support, 1, 0);
        widthProperty().addListener((o, before, width) -> {
            boolean stacked = width.doubleValue() < 430;
            setHgap(stacked ? 0 : 10);
            first.setPercentWidth(stacked ? 100 : 50);
            second.setPercentWidth(stacked ? 0 : 50);
            setColumnIndex(support, stacked ? 0 : 1);
            setRowIndex(support, stacked ? 1 : 0);
        });
    }

    private static VBox card(String style) {
        var card = new VBox(8);
        card.getStyleClass().add(style);
        card.setMinWidth(0);
        card.setPrefWidth(0);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setMinHeight(148);
        setHgrow(card, Priority.ALWAYS);
        setFillWidth(card, true);

        return card;
    }

    private static Label caption(String text) {
        var label = new Label(text);
        label.getStyleClass().add("support-caption");
        label.setWrapText(true);
        label.setMinWidth(0);

        return label;
    }

    private static Button supportLink(String title, String icon, String hint, String url) {
        var button = new Button(title, ShellIcon.create(icon));
        button.getStyleClass().add("support-button");
        button.setGraphicTextGap(6);
        button.setMinWidth(0);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setTooltip(ShellTooltip.create(hint));
        button.setAccessibleText(hint);
        button.setOnAction(e -> UrlUtils.openLink(url));

        return button;
    }
}
