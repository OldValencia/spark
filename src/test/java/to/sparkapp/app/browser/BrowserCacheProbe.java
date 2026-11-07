package to.sparkapp.app.browser;

/** Runs a single visit in a fresh JVM so cache reuse cannot come from a live page. */
public final class BrowserCacheProbe {
    public static void main(String[] args) throws Exception {
        if (!Boolean.getBoolean("spark.nativeTests") ||
                !System.getenv("WEBVIEW2_USER_DATA_FOLDER").contains("native-tests")) {
            throw new IllegalStateException("An isolated test profile is required");
        }

        try (var browser = new WindowsBrowserTest.Harness()) {
            browser.load(args[0]);
            browser.eval("window.probe(String(window.cacheMarker))");

            if (!"cached-script".equals(browser.message())) {
                throw new IllegalStateException("The cached script must execute on every visit");
            }
        }

        System.out.println("CACHE_VISIT_COMPLETE");
    }
}
