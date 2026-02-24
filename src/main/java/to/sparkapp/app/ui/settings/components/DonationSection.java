package to.sparkapp.app.ui.settings.components;

import to.sparkapp.app.utils.UrlUtils;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;

public class DonationSection extends HBox {

    public DonationSection() {
        this.setAlignment(Pos.CENTER);
        this.setSpacing(10);
        this.setMaxWidth(Double.MAX_VALUE);

        var coffeeColor = Color.rgb(255, 200, 0);
        var bmcBtn = new ColorfulButton("☕ Buy me a coffee", coffeeColor, () -> UrlUtils.openLink("https://buymeacoffee.com/oldvalencia"));

        var kofiColor = Color.rgb(255, 94, 91);
        var kofiBtn = new ColorfulButton("❤️ Ko-Fi", kofiColor, () -> UrlUtils.openLink("https://ko-fi.com/oldvalencia"));

        this.getChildren().addAll(bmcBtn, kofiBtn);
    }
}
