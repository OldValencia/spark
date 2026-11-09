package to.sparkapp.app.browser;

import static org.junit.jupiter.api.Assertions.*;

import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.Test;

class SiteShortcutTest {
    @Test
    void digitsOpenSitesByPositionAndNineOpensTheLast() {
        assertEquals(0, shortcut(KeyCode.DIGIT1).targetIndex(3, 5));
        assertEquals(1, shortcut(KeyCode.NUMPAD2).targetIndex(3, 5));
        assertEquals(7, shortcut(KeyCode.DIGIT8).targetIndex(0, 12));
        assertEquals(11, shortcut(KeyCode.DIGIT9).targetIndex(0, 12));
        assertEquals(2, shortcut(KeyCode.DIGIT9).targetIndex(0, 3));
    }

    @Test
    void positionPastTheLastSiteDoesNothing() {
        assertEquals(-1, shortcut(KeyCode.DIGIT4).targetIndex(0, 3));
        assertEquals(-1, shortcut(KeyCode.DIGIT1).targetIndex(-1, 0));
    }

    @Test
    void tabMovesThroughSitesInACircle() {
        var next = SiteShortcut.fromKey(KeyCode.TAB.getCode(), false);
        var previous = SiteShortcut.fromKey(KeyCode.TAB.getCode(), true);

        assertEquals(2, next.targetIndex(1, 3));
        assertEquals(0, next.targetIndex(2, 3));
        assertEquals(2, previous.targetIndex(0, 3));
        assertEquals(0, previous.targetIndex(1, 3));
    }

    @Test
    void otherKeysAndShiftedDigitsStayWithThePage() {
        assertNull(SiteShortcut.fromKey(KeyCode.DIGIT0.getCode(), false));
        assertNull(SiteShortcut.fromKey(KeyCode.A.getCode(), false));
        assertNull(SiteShortcut.fromKey(KeyCode.DIGIT2.getCode(), true));
    }

    @Test
    void hintsMatchTheShortcuts() {
        assertEquals("Ctrl+1", SiteShortcut.hintFor(0, 3));
        assertEquals("Ctrl+3", SiteShortcut.hintFor(2, 3));
        assertEquals("Ctrl+8", SiteShortcut.hintFor(7, 12));
        assertNull(SiteShortcut.hintFor(9, 12));
        assertEquals("Ctrl+9", SiteShortcut.hintFor(11, 12));
    }

    private static SiteShortcut shortcut(KeyCode key) {
        return SiteShortcut.fromKey(key.getCode(), false);
    }
}
