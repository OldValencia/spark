package to.sparkapp.app.browser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Builds the tab/window query adapter. The bootstrap variant runs first in an extension's background
 * code; the document variant runs in Spark's popup host and announces the selected site's window.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ExtensionContextScript {
    private static final String TEMPLATE = BrowserResources.text("/js/extension-context.js");
    private static final String EXTENSION_ID = "__SPARK_EXTENSION_ID__";
    private static final String WINDOW_ID = "__SPARK_WINDOW_ID__";
    private static final String BOOTSTRAP = "__SPARK_BOOTSTRAP__";
    private static final ObjectMapper JSON = new ObjectMapper();

    public static String bootstrap() {
        return fill("chrome.runtime.id", "null", true);
    }

    static String create(String extensionId, Integer windowId) {
        try {
            return fill(JSON.writeValueAsString(extensionId), String.valueOf(windowId), false);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Could not serialize extension context", error);
        }
    }

    private static String fill(String extensionId, String windowId, boolean bootstrap) {
        return TEMPLATE.replace(EXTENSION_ID, extensionId)
                .replace(WINDOW_ID, windowId)
                .replace(BOOTSTRAP, String.valueOf(bootstrap));
    }
}
