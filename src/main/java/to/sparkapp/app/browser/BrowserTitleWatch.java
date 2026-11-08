package to.sparkapp.app.browser;

import lombok.Setter;

/**
 * Reports when a page of the site changes its title, which sites do when something new happens
 * there. Runs on the owning WebView2 thread.
 */
final class BrowserTitleWatch {
    private static final int CORE_ADD_DOCUMENT_TITLE_CHANGED = 46;
    private static final int CORE_REMOVE_DOCUMENT_TITLE_CHANGED = 47;
    private static final String DOCUMENT_TITLE_CHANGED_HANDLER = "f5f2b923-953e-4042-9f95-f3a118e1afd4";

    @Setter
    private Runnable changed = () -> {
    };

    void install(BrowserContext context) {
        context.getEvents().listen(context.getCore(), CORE_ADD_DOCUMENT_TITLE_CHANGED,
                CORE_REMOVE_DOCUMENT_TITLE_CHANGED, DOCUMENT_TITLE_CHANGED_HANDLER, args -> changed.run());
    }
}
