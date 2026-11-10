package to.sparkapp.app.ui.topbar.components;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MascotReliefTest {
    private static final long SECOND = 1_000_000_000L;

    private final MascotRelief relief = new MascotRelief();

    @Test
    void aShortLoadIsNotCelebrated() {
        relief.changed(MascotMood.THINKING, 10 * SECOND);

        assertFalse(relief.changed(MascotMood.CALM, 11 * SECOND));
    }

    @Test
    void aLongLoadIsCelebrated() {
        relief.changed(MascotMood.THINKING, 10 * SECOND);

        assertTrue(relief.changed(MascotMood.CALM, 13 * SECOND));
    }

    @Test
    void aPageThatLoadsRightAfterFailingIsCelebrated() {
        relief.changed(MascotMood.SAD, 10 * SECOND);
        relief.changed(MascotMood.CALM, 20 * SECOND);
        relief.changed(MascotMood.THINKING, 20 * SECOND + SECOND / 2);

        assertTrue(relief.changed(MascotMood.CALM, 21 * SECOND));
    }

    @Test
    void leavingAFailedPageForAnotherIsNotARecovery() {
        relief.changed(MascotMood.SAD, 10 * SECOND);

        assertFalse(relief.changed(MascotMood.CALM, 20 * SECOND));

        relief.changed(MascotMood.THINKING, 30 * SECOND);
        assertFalse(relief.changed(MascotMood.CALM, 31 * SECOND), "A later quick load is not a retry");
    }
}
