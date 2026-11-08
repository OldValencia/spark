package to.sparkapp.app.browser;

/**
 * A keyboard shortcut that switches the open site, as in a tabbed browser: Ctrl + 1…8 opens a site
 * by position, Ctrl + 9 the last one, Ctrl + Tab and Ctrl + Shift + Tab the next and previous one.
 *
 * <p>Keys are Windows virtual-key codes, which JavaFX key codes share for these keys.
 */
public record SiteShortcut(Kind kind, int position) {
    public static final int NUMBERED_SITES = 8;
    private static final int KEY_TAB = 0x09;
    private static final int KEY_1 = 0x31;
    private static final int KEY_9 = 0x39;
    private static final int KEY_NUMPAD_1 = 0x61;
    private static final int KEY_NUMPAD_9 = 0x69;

    public enum Kind {
        POSITION,
        LAST,
        NEXT,
        PREVIOUS
    }

    /**
     * Reads a key pressed together with Ctrl and without Alt. Ctrl + Alt is AltGr on many layouts,
     * so it must stay with the page.
     *
     * @return the shortcut, or {@code null} when the key belongs to the page
     */
    public static SiteShortcut fromKey(int keyCode, boolean shift) {
        if (keyCode == KEY_TAB) {
            return new SiteShortcut(shift ? Kind.PREVIOUS : Kind.NEXT, 0);
        }

        // Sites use Ctrl + Shift + digit for their own commands.
        if (shift) {
            return null;
        }

        var digit = digitOf(keyCode);

        if (digit == 0) {
            return null;
        }

        return digit == 9 ? new SiteShortcut(Kind.LAST, 0) : new SiteShortcut(Kind.POSITION, digit);
    }

    /**
     * @return the index of the site to open, or -1 when the shortcut points past the last site
     */
    public int targetIndex(int currentIndex, int siteCount) {
        if (siteCount <= 0) {
            return -1;
        }

        return switch (kind) {
            case POSITION -> position <= siteCount ? position - 1 : -1;
            case LAST -> siteCount - 1;
            case NEXT -> Math.floorMod(currentIndex + 1, siteCount);
            case PREVIOUS -> Math.floorMod(currentIndex - 1, siteCount);
        };
    }

    /** The shortcut shown for the site at an index, or {@code null} when it has none. */
    public static String hintFor(int index, int siteCount) {
        if (index < NUMBERED_SITES) {
            return "Ctrl+" + (index + 1);
        }

        return index == siteCount - 1 ? "Ctrl+9" : null;
    }

    private static int digitOf(int keyCode) {
        if (keyCode >= KEY_1 && keyCode <= KEY_9) {
            return keyCode - KEY_1 + 1;
        }

        if (keyCode >= KEY_NUMPAD_1 && keyCode <= KEY_NUMPAD_9) {
            return keyCode - KEY_NUMPAD_1 + 1;
        }

        return 0;
    }
}
