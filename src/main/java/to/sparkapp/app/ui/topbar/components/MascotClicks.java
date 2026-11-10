package to.sparkapp.app.ui.topbar.components;

/** Counts clicks on the mascot: each one is a hello, but enough of them in a row make it dizzy. */
final class MascotClicks {
    static final int DIZZY_AFTER = 5;
    static final long ROW_NANOS = 2_500_000_000L;

    private long rowStarted;
    private int inRow;

    MascotReaction click() {
        var now = System.nanoTime();

        if (inRow == 0 || now - rowStarted > ROW_NANOS) {
            rowStarted = now;
            inRow = 0;
        }

        inRow++;

        if (inRow >= DIZZY_AFTER) {
            inRow = 0;

            return MascotReaction.DIZZY;
        }

        return MascotReaction.HAPPY;
    }
}
