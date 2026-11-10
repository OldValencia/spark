package to.sparkapp.app.ui.topbar.components;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MascotClicksTest {
    @Test
    void eachClickIsAHelloUntilEnoughInARowMakeItDizzy() {
        var clicks = new MascotClicks();

        for (var click = 1; click < MascotClicks.DIZZY_AFTER; click++) {
            assertEquals(MascotReaction.HAPPY, clicks.click());
        }

        assertEquals(MascotReaction.DIZZY, clicks.click());
        assertEquals(MascotReaction.HAPPY, clicks.click(), "The count starts over after a dizzy spell");
    }
}
