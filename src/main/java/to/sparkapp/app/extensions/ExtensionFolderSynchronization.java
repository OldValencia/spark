package to.sparkapp.app.extensions;

import static to.sparkapp.app.extensions.ExtensionSynchronization.BROWSER_TIMEOUT_SECONDS;
import static to.sparkapp.app.extensions.ExtensionSynchronization.elapsedMillis;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import to.sparkapp.app.browser.BrowserExtension;
import to.sparkapp.app.browser.WebviewManager;
import to.sparkapp.app.extensions.ExtensionStore.Saved;

/**
 * Brings one managed folder up to date: refreshes Spark's bootstrap, prepares startup files, and
 * reinstalls the extension only when its files changed. A failure is reported on that folder's entry.
 */
@Slf4j
@RequiredArgsConstructor
final class ExtensionFolderSynchronization {
    private final WebviewManager browser;
    private final ExtensionStore store;
    private final List<BrowserExtension> installed;
    private final Consumer<String> progress;
    private final ExtensionPreparation preparation = new ExtensionPreparation();

    ExtensionEntry synchronize(Path directory, String position) {
        var startedAt = System.nanoTime();
        ExtensionManifest manifest = null;
        try {
            manifest = ExtensionManifest.read(directory);
            var label = manifest.name() + " (" + position + ")…";
            progress.accept("Checking " + label);

            var saved = store.get(directory);
            var extension = findInstalled(saved);
            var bootstrapUpdated = updateBootstrap(directory, extension, label);
            var prepared = prepare(directory, extension, label);

            var fingerprint = ExtensionFingerprint.of(directory);
            var unchanged = saved != null && ExtensionFingerprint.matches(directory, fingerprint, saved.fingerprint());
            if (prepared || bootstrapUpdated || extension == null || !unchanged) {
                progress.accept("Loading " + label);
                extension = install(directory, saved);
            }

            store.put(directory, new Saved(extension.id(), fingerprint, extension.enabled()));
            log.info("Extension synchronized: name={}, prepared={}, bootstrapUpdated={}, elapsedMs={}",
                    manifest.name(), prepared, bootstrapUpdated, elapsedMillis(startedAt));

            return new ExtensionEntry(manifest, extension.id(), extension.enabled(), null);
        } catch (Exception error) {
            var described = manifest != null ? manifest : ExtensionManifest.placeholder(directory);
            return new ExtensionEntry(described, null, false, ExtensionManager.message(error));
        }
    }

    private boolean updateBootstrap(Path directory, BrowserExtension extension, String label) throws Exception {
        if (!preparation.bootstrapOutdated(directory)) {
            return false;
        }

        progress.accept("Updating " + label);
        whileDisabled(extension, () -> preparation.updateBootstrap(directory));
        return true;
    }

    private boolean prepare(Path directory, BrowserExtension extension, String label) throws Exception {
        if (!preparation.needed(directory)) {
            return false;
        }

        progress.accept("Preparing " + label);
        whileDisabled(extension, () -> preparation.prepare(directory));
        return true;
    }

    private BrowserExtension findInstalled(Saved saved) {
        if (saved == null) {
            return null;
        }

        return installed.stream()
                .filter(item -> item.id().equals(saved.id()))
                .findFirst()
                .orElse(null);
    }

    /** Installing an existing path reloads it under the same ID; the saved enabled state is restored. */
    private BrowserExtension install(Path directory, Saved saved) throws Exception {
        var startedAt = System.nanoTime();
        var extension = browser.installExtension(directory).get(BROWSER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (saved != null) {
            browser.setExtensionEnabled(extension.id(), saved.enabled()).get(BROWSER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            extension = new BrowserExtension(extension.id(), extension.name(), saved.enabled());
        }
        log.info("Extension browser installation finished: folder={}, elapsedMs={}", directory.getFileName(), elapsedMillis(startedAt));

        return extension;
    }

    /** Startup files are changed only while the extension is not running; installation reloads it. */
    private void whileDisabled(BrowserExtension extension, FileChange change) throws Exception {
        var restoreEnabled = extension != null && extension.enabled();
        if (restoreEnabled) {
            browser.setExtensionEnabled(extension.id(), false).get(BROWSER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        try {
            change.apply();
        } catch (Exception error) {
            if (restoreEnabled) {
                enableAfterFailure(extension, error);
            }
            throw error;
        }
    }

    private void enableAfterFailure(BrowserExtension extension, Exception failure) {
        try {
            browser.setExtensionEnabled(extension.id(), true).get(BROWSER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception restoreError) {
            failure.addSuppressed(restoreError);
        }
    }

    private interface FileChange {
        void apply() throws Exception;
    }
}
