package to.sparkapp.app.ui.topbar.components;

/**
 * Notices when a page arrives after a wait worth celebrating: a long load, or a load that came right after the page
 * failed. Switching away from a failed page to another one is not a recovery.
 */
final class MascotRelief {
    static final long LONG_WAIT_NANOS = 2_000_000_000L;
    /** A retry starts loading this soon after the failure is cleared. */
    static final long RETRY_NANOS = 1_500_000_000L;

    private MascotMood previous = MascotMood.CALM;
    private long thinkingSince;
    private long sadEndedAt = Long.MIN_VALUE;
    private boolean retrying;

    /** @return whether the mood change is a relief worth a happy reaction */
    boolean changed(MascotMood mood) {
        return changed(mood, System.nanoTime());
    }

    boolean changed(MascotMood mood, long now) {
        var before = previous;
        previous = mood;

        if (before == MascotMood.SAD) {
            sadEndedAt = now;
        }

        if (mood == MascotMood.THINKING) {
            thinkingSince = now;
            retrying = sadEndedAt != Long.MIN_VALUE && now - sadEndedAt <= RETRY_NANOS;

            return false;
        }

        if (before != MascotMood.THINKING || mood != MascotMood.CALM) {
            return false;
        }

        return retrying || now - thinkingSince >= LONG_WAIT_NANOS;
    }
}
