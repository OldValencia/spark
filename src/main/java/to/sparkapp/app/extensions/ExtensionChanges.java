package to.sparkapp.app.extensions;

import java.util.concurrent.TimeUnit;

import lombok.RequiredArgsConstructor;
import to.sparkapp.app.browser.WebviewManager;
import to.sparkapp.app.extensions.ExtensionStore.Saved;

/** Applies a user's change to one extension in both the browser profile and the folder index. */
@RequiredArgsConstructor
final class ExtensionChanges {
    private final WebviewManager browser;
    private final ExtensionStore store;

    void setEnabled(ExtensionEntry entry, boolean enabled) throws Exception {
        var directory = entry.manifest().directory();
        var saved = store.get(directory);
        var id = installedId(entry, saved);
        if (id != null) {
            browser.setExtensionEnabled(id, enabled).get(ExtensionSynchronization.BROWSER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
        if (saved != null) {
            store.put(directory, saved.withEnabled(enabled));
        }
    }

    /** Unloads the extension and archives its folder; a locked folder is excluded until it can be moved. */
    void remove(ExtensionEntry entry) throws Exception {
        var directory = entry.manifest().directory();
        var id = installedId(entry, store.get(directory));
        if (id != null) {
            browser.removeExtension(id).get(ExtensionSynchronization.BROWSER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            store.forgetInstallation(id);
        }
        store.remove(directory);
    }

    /** Returns the browser ID only when the extension is currently installed in the profile. */
    private String installedId(ExtensionEntry entry, Saved saved) throws Exception {
        var id = entry.id() != null ? entry.id() : saved == null ? null : saved.id();
        if (id == null) {
            return null;
        }

        var installed = browser.listExtensions().get(ExtensionSynchronization.BROWSER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        return installed.stream().anyMatch(item -> item.id().equals(id)) ? id : null;
    }
}
