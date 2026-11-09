package to.sparkapp.app.windows;

import static org.junit.jupiter.api.Assertions.*;

import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;

class WindowFrameOutlineTest {
    @Test
    void outlineTakesAHintOfTheSiteColor() {
        var outline = WindowFrame.outlineFor(Color.web("#FD6F00"));

        assertTrue(outline.getRed() > WindowFrame.NEUTRAL_OUTLINE.getRed());
        assertTrue(outline.getBlue() < WindowFrame.NEUTRAL_OUTLINE.getBlue());
        assertTrue(outline.getRed() < 0.6, "The site color must stay a hint, not a bright frame");
    }

    @Test
    void darkSiteColorsKeepTheNeutralOutlineVisible() {
        assertEquals(WindowFrame.NEUTRAL_OUTLINE, WindowFrame.outlineFor(Color.BLACK));
        assertEquals(WindowFrame.NEUTRAL_OUTLINE, WindowFrame.outlineFor(Color.web("#1b1b2e")));
    }
}
