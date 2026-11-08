package to.sparkapp.app.browser;

import static to.sparkapp.app.browser.ComCalls.call;
import static to.sparkapp.app.browser.ComCalls.check;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.ptr.PointerByReference;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** A document host has no Chromium new-tab page to navigate to. */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ExtensionPopupNavigation {
    private static final int CORE_ADD_NAVIGATION_STARTING = 7;
    private static final int CORE_REMOVE_NAVIGATION_STARTING = 8;
    private static final int ARGS_URI = 3;
    private static final int ARGS_PUT_CANCEL = 8;
    private static final String NAVIGATION_STARTING = "9adbe429-f36d-432b-9ddc-f8881fbd76e3";
    private static final String NEW_TAB_PAGE_SCHEME = "chrome-search:";

    static void install(Webview view) {
        var context = view.windows().activeContext();
        context.getEvents().listen(context.getCore(), CORE_ADD_NAVIGATION_STARTING, CORE_REMOVE_NAVIGATION_STARTING,
                NAVIGATION_STARTING, ExtensionPopupNavigation::cancelNewTabPage);
    }

    private static void cancelNewTabPage(Pointer args) {
        var value = new PointerByReference();
        check(call(args, ARGS_URI, value));
        try {
            if (value.getValue().getWideString(0).startsWith(NEW_TAB_PAGE_SCHEME)) {
                check(call(args, ARGS_PUT_CANCEL, 1));
                log.info("Extension popup canceled navigation to an unavailable Chromium new-tab page");
            }
        } finally {
            Ole32.INSTANCE.CoTaskMemFree(value.getValue());
        }
    }
}
