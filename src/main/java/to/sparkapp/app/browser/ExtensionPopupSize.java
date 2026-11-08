package to.sparkapp.app.browser;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;

/** A document size reported by the popup page, in CSS pixels at the given device pixel ratio. */
record ExtensionPopupSize(double width, double height, double ratio) {
    /** Installed at document creation; reports size changes as prefixed web messages. */
    static final String SCRIPT = BrowserResources.text("/js/extension-popup-size.js");
    static final String MESSAGE_PREFIX = "spark-popup-size:";
    static final String POLL_SCRIPT = "globalThis[Symbol.for('spark.popupSize')]?.() ?? null";
    static final String REMEASURE_SCRIPT = "globalThis[Symbol.for('spark.popupMeasure')]?.()";
    private static final ObjectMapper JSON = new ObjectMapper();

    /** Returns null when the page reported nothing usable yet. */
    static ExtensionPopupSize parse(String value) throws IOException {
        var data = JSON.readTree(value);
        if (data == null || data.isNull()) {
            return null;
        }

        var size = new ExtensionPopupSize(data.path("width").asDouble(), data.path("height").asDouble(),
                data.path("ratio").asDouble(1));
        return size.valid() ? size : null;
    }

    private boolean valid() {
        return Double.isFinite(width) && Double.isFinite(height) && Double.isFinite(ratio)
                && width > 0 && height > 0 && ratio > 0;
    }
}
