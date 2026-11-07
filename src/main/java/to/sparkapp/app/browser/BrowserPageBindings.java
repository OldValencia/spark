package to.sparkapp.app.browser;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import to.sparkapp.app.utils.SystemUtils;
import to.sparkapp.app.utils.UrlUtils;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class BrowserPageBindings {
    static void install(Webview webview, WebviewNavigator navigator, WebviewZoomManager zoomManager, Runnable documentReady) {
        if (webview == null) {
            return;
        }

        var api = new SparkJsApi(webview);

        api.on("documentReady", args -> {
            if (!SystemUtils.isWindows()) {
                documentReady.run();
            }
        });
        api.on("zoom", args -> {
            if (!args.isEmpty()) {
                zoomManager.handleZoomCommand(args.get(0).asText());
            }
        });

        api.on("urlChanged", args -> {
            if (!SystemUtils.isWindows() && !args.isEmpty()) {
                navigator.handleUrlChange(args.get(0).asText());
            }
        });

        api.on("middleClicked", args -> {
            if (!args.isEmpty()) {
                UrlUtils.openLink(args.get(0).asText());
            }
        });

        if (SystemUtils.isWindows()) {
            webview.windows().enablePages(BrowserPageScripts.INIT_SCRIPT, api::receive);
        }
    }

}
