package to.sparkapp.app.browser;

/** Waits for consecutive matching measurements instead of presenting intermediate layouts. */
final class ExtensionPopupSizeStability {
    private ExtensionPopupSize previous;

    boolean accept(ExtensionPopupSize size) {
        var stable = previous != null && Math.abs(size.width() - previous.width()) <= 1
                && Math.abs(size.height() - previous.height()) <= 1
                && Math.abs(size.ratio() - previous.ratio()) < 0.01;
        previous = size;
        return stable;
    }
}
