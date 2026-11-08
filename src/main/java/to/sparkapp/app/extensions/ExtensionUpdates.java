package to.sparkapp.app.extensions;

import static to.sparkapp.app.extensions.ExtensionSynchronization.BROWSER_TIMEOUT_SECONDS;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import to.sparkapp.app.browser.WebviewManager;

/**
 * Updates extensions in place. The folder path stays the same, so the browser keeps the extension's
 * ID and stored data; synchronization then prepares and reloads the new version.
 */
@Slf4j
@RequiredArgsConstructor
final class ExtensionUpdates {
    private final WebviewManager browser;
    private final ExtensionStore store;
    private final ExtensionUpdateService service;

    /** Folders whose update service offers a newer version, with that version. */
    Map<Path, String> check() throws Exception {
        var browserVersion = browserVersion();
        var available = new LinkedHashMap<Path, String>();
        for (var directory : store.availableDirectories()) {
            var source = ExtensionSource.read(directory).filter(ExtensionSource::updatable);
            if (source.isEmpty()) {
                continue;
            }
            try {
                var current = ExtensionManifest.read(directory).version();
                service.check(source.get(), current, browserVersion)
                        .ifPresent(update -> available.put(directory, update.version()));
            } catch (Exception error) {
                log.warn("Extension update check failed: folder={}", directory.getFileName(), error);
            }
        }

        return available;
    }

    /** Downloads and installs a newer version; returns false when the extension is already current. */
    boolean update(Path directory) throws Exception {
        var source = ExtensionSource.read(directory).filter(ExtensionSource::updatable)
                .orElseThrow(() -> new IOException("This extension has no update service. Import a newer package to update it."));
        var current = ExtensionManifest.read(directory).version();
        var update = service.check(source, current, browserVersion());
        if (update.isEmpty()) {
            return false;
        }

        var download = service.download(update.get(), store.root());
        try {
            replace(directory, new ExtensionImporter().importForUpdate(download, store.root()));
        } finally {
            Files.deleteIfExists(download);
        }
        log.info("Extension updated: folder={}, from={}, to={}", directory.getFileName(), current, update.get().version());

        return true;
    }

    /** Downloads the current version from the Chrome Web Store; an installed copy is updated in place. */
    void installFromStore(String storeId) throws Exception {
        var source = new ExtensionSource(storeId, ChromeWebStore.UPDATE_URL);
        var update = service.check(source, "0.0.0.0", browserVersion())
                .orElseThrow(() -> new IOException("The Chrome Web Store did not offer this extension. Check the ID or link."));
        var download = service.download(update, store.root());
        try {
            importArchive(download);
        } finally {
            Files.deleteIfExists(download);
        }
        log.info("Extension installed from the Chrome Web Store: storeId={}, version={}", storeId, update.version());
    }

    /** Imports a package; an installed copy of the same extension is updated in place instead of duplicated. */
    void importArchive(Path archive) throws Exception {
        var imported = new ExtensionImporter().importForUpdate(archive, store.root());
        var installed = findInstalled(imported);
        if (installed.isPresent()) {
            replace(installed.get(), imported);
            log.info("Imported package updated an installed extension: folder={}", installed.get().getFileName());
        } else {
            ExtensionFiles.moveWhenReady(imported, store.root().resolve("extension-" + UUID.randomUUID()));
        }
    }

    /** Matches by store ID when both packages have one, otherwise by extension name. */
    private Optional<Path> findInstalled(Path imported) throws Exception {
        var name = ExtensionManifest.read(imported).name();
        var storeId = ExtensionSource.read(imported).map(ExtensionSource::storeId).orElse(null);
        for (var directory : store.availableDirectories()) {
            var installedStoreId = ExtensionSource.read(directory).map(ExtensionSource::storeId).orElse(null);
            if (storeId != null && installedStoreId != null) {
                if (storeId.equals(installedStoreId)) {
                    return Optional.of(directory);
                }
            } else if (name.equals(ExtensionManifest.read(directory).name())) {
                return Optional.of(directory);
            }
        }

        return Optional.empty();
    }

    /** Stops the running extension, swaps the folders, and restores the extension if swapping fails. */
    private void replace(Path installed, Path updated) throws Exception {
        var saved = store.get(installed);
        var running = saved != null && saved.enabled() && saved.id() != null && isInstalled(saved.id());
        if (running) {
            browser.setExtensionEnabled(saved.id(), false).get(BROWSER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        try {
            ExtensionReplacement.replace(installed, updated);
        } catch (Exception error) {
            ExtensionFiles.deleteTree(updated);
            if (running) {
                browser.setExtensionEnabled(saved.id(), true).get(BROWSER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
            throw error;
        }
    }

    private boolean isInstalled(String id) throws Exception {
        var installed = browser.listExtensions().get(BROWSER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        return installed.stream().anyMatch(extension -> extension.id().equals(id));
    }

    private String browserVersion() throws Exception {
        return browser.browserVersion().get(BROWSER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }
}
