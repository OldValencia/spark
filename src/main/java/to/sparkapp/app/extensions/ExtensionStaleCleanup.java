package to.sparkapp.app.extensions;

import static to.sparkapp.app.extensions.ExtensionSynchronization.BROWSER_TIMEOUT_SECONDS;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import to.sparkapp.app.browser.BrowserExtension;
import to.sparkapp.app.browser.WebviewManager;

/**
 * Unloads Spark-installed extensions whose folders were deleted, so they stop using memory.
 * Built-in WebView2 components are never managed and are left alone.
 */
@Slf4j
@RequiredArgsConstructor
final class ExtensionStaleCleanup {
    private final WebviewManager browser;
    private final ExtensionStore store;

    void run(List<BrowserExtension> installed, List<Path> directories) {
        var known = store.knownIds(directories);
        for (var extension : installed) {
            if (!known.contains(extension.id()) && store.isManaged(extension.id())) {
                remove(extension);
            }
        }

        try {
            store.forgetMissing(directories);
        } catch (Exception error) {
            log.warn("Could not prune the extension index", error);
        }
    }

    private void remove(BrowserExtension extension) {
        try {
            browser.removeExtension(extension.id()).get(BROWSER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            store.forgetInstallation(extension.id());
            log.info("Removed an extension without a managed folder: name={}, id={}", extension.name(), extension.id());
        } catch (Exception error) {
            log.warn("Could not remove an extension without a managed folder: id={}", extension.id(), error);
        }
    }
}
