package to.sparkapp.app.ui;

import javafx.scene.paint.Color;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SiteAccent {
    public static final Color DEFAULT = Color.web("#bcbcc6");

    public static Color resolve(String value) {
        try {
            return Color.web(value);
        } catch (IllegalArgumentException | NullPointerException invalidColor) {
            return DEFAULT;
        }
    }
}
