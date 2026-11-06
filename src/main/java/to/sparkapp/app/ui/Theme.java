package to.sparkapp.app.ui;

import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Theme {

    public static final Color BG_DEEP = Color.rgb(22, 22, 24);
    public static final Color BG_BAR = Color.web("#25262a");
    public static final Color BG_POPUP = Color.web("#2d2e33");
    public static final Color BG_HOVER = Color.rgb(42, 42, 46);

    public static final Color TEXT_PRIMARY = Color.rgb(230, 230, 232);
    public static final Color TEXT_SECONDARY = Color.rgb(160, 160, 165);
    public static final Color TEXT_TERTIARY = Color.rgb(120, 120, 125);

    public static final Color ACCENT = Color.web("#c6c6d2");
    public static final Color BORDER = Color.web("#414247");

    /** Elevated surface used by the settings cards. */
    public static final Color BG_CARD = Color.web("#2d2e33");
    /** Hairline between rows inside a card. */
    public static final Color SEPARATOR = Color.rgb(48, 48, 52);
    public static final Color BG_ROW_HOVER = Color.rgb(45, 45, 49);
    public static final Color DANGER = Color.rgb(255, 94, 91);

    public static final int CARD_RADIUS = 14;
    public static final int ROW_RADIUS = 10;

    public static final Color BTN_HOVER_SETTINGS = Color.rgb(140, 140, 150);
    public static final Color BTN_HOVER_CLOSE = Color.rgb(255, 80, 80);
    public static final Color BTN_RING = Color.rgb(80, 80, 90);

    public static final Color TOGGLE_BG_OFF = Color.rgb(60, 60, 64);
    public static final Color TOGGLE_BG_ON = Color.web("#6da784");
    public static final Color TOGGLE_THUMB = Color.rgb(250, 250, 252);

    public static final String FONT_NAME = resolveFontName();
    public static final Font FONT_RIGHT_TOP_BAR_AREA = Font.font("SansSerif", 17);
    public static final Font FONT_SELECTOR = Font.font(FONT_NAME, 14);
    public static final Font FONT_SETTINGS = Font.font(FONT_NAME, 13);
    public static final Font FONT_SETTINGS_SECTION = Font.font(FONT_NAME, FontWeight.BOLD, 12);
    public static final Font FONT_SETTINGS_TITLE = Font.font(FONT_NAME, FontWeight.MEDIUM, 13);
    public static final Font FONT_SETTINGS_HINT = Font.font(FONT_NAME, 11.5);

    public static String toHex(Color color) {
        return String.format("#%02X%02X%02X",
                (int) (color.getRed() * 255),
                (int) (color.getGreen() * 255),
                (int) (color.getBlue() * 255));
    }

    public static String toHexWithAlpha(Color color) {
        return String.format("#%02X%02X%02X%02X",
                (int) (color.getRed() * 255),
                (int) (color.getGreen() * 255),
                (int) (color.getBlue() * 255),
                (int) (color.getOpacity() * 255));
    }

    public static Color lerp(Color a, Color b, double t) {
        t = clamp(t);
        return new Color(
                a.getRed() + (b.getRed() - a.getRed()) * t,
                a.getGreen() + (b.getGreen() - a.getGreen()) * t,
                a.getBlue() + (b.getBlue() - a.getBlue()) * t,
                a.getOpacity() + (b.getOpacity() - a.getOpacity()) * t
        );
    }

    public static Color withAlpha(Color c, double alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), clamp(alpha));
    }

    private static double clamp(double v) {
        return Math.clamp(v, 0.0, 1.0);
    }

    private static String resolveFontName() {
        var available = Font.getFamilies();
        if (available.contains("SF Pro Display")) {
            return "SF Pro Display";
        }
        if (available.contains("SF Pro Text")) {
            return "SF Pro Text";
        }
        if (available.contains("Segoe UI")) {
            return "Segoe UI";
        }
        return "SansSerif";
    }
}
