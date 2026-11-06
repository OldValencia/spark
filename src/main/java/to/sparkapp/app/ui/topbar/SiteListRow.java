package to.sparkapp.app.ui.topbar;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import to.sparkapp.app.config.AiConfiguration.AiConfig;
import to.sparkapp.app.ui.topbar.components.AiDock;
import to.sparkapp.app.ui.topbar.components.ShellIcon;
import to.sparkapp.app.utils.UrlUtils;

final class SiteListRow extends HBox {
    SiteListRow(SiteListContext context, SiteListDrag drag, SiteListCell cell, AiConfig site) {
        super(3);

        var select = createSelection(context, site);
        var edit = new Button();
        edit.setGraphic(ShellIcon.create("pencil-square"));
        edit.getStyleClass().add("shell-button");
        edit.setAccessibleText("Edit " + site.name());
        edit.setOnAction(event -> context.edit().accept(site));

        setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(select, Priority.ALWAYS);
        getChildren().addAll(new SiteListGrip(context, drag, cell, site), select, edit);
    }

    private Button createSelection(SiteListContext context, AiConfig site) {
        var name = new Label(site.name() + (site.id().equals(context.dock().getSelectedId()) ? "  ✓" : ""));
        name.getStyleClass().add("shell-site-name");
        name.setMaxWidth(210);

        var domain = new Label(UrlUtils.host(site.url()));
        domain.setMaxWidth(210);
        domain.getStyleClass().add("shell-site-domain");

        var metadata = new VBox(3, name, domain);
        metadata.setMinWidth(0);

        var content = new HBox(10, createIcon(site), metadata);
        content.setAlignment(Pos.CENTER_LEFT);

        var select = new Button();
        select.getStyleClass().add("shell-row");
        select.setGraphic(content);
        select.setMaxWidth(Double.MAX_VALUE);
        select.setMinWidth(0);
        select.setAccessibleText("Open " + site.name());
        select.setOnAction(event -> context.choose().accept(site));

        return select;
    }

    private Node createIcon(AiConfig site) {
        var image = site.icon() == null ? null : AiDock.ICON_CACHE.get(site.icon());

        if (image == null || image.getWidth() <= 1) {
            return new Label("↗");
        }

        var view = new ImageView(image);
        view.setFitWidth(20);
        view.setFitHeight(20);
        view.setPreserveRatio(true);

        return view;
    }
}
