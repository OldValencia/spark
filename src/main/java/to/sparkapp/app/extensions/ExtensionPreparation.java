package to.sparkapp.app.extensions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import to.sparkapp.app.browser.ExtensionContextScript;

/** Keeps the installed path stable and archives original bytes before committing a prepared copy. */
@Slf4j
final class ExtensionPreparation {
    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * Returns true for unprepared folders and for folders whose prepared files were replaced by a new
     * extension version. Prepared files that were edited by hand are rejected.
     */
    boolean needed(Path directory) throws IOException {
        var marker = new ExtensionPreparationMarker(directory);
        if (!marker.exists()) {
            return hasBackground(directory);
        }

        var bootstrap = bootstrapFile(directory).toAbsolutePath().normalize();
        var changed = marker.changedFiles();
        changed.removeIf(file -> file.toAbsolutePath().normalize().equals(bootstrap));
        if (changed.isEmpty()) {
            return false;
        }
        for (var file : changed) {
            if (Files.isRegularFile(file) && Files.readString(file).contains(ExtensionBootstrap.BOOTSTRAP_FILE)) {
                throw new IOException("The prepared extension startup files changed. Reimport the package.");
            }
        }

        log.info("Extension startup files were replaced by a new version and will be prepared again: folder={}", directory);
        return hasBackground(directory);
    }

    /** True when a prepared folder contains a Spark bootstrap from a different build. */
    boolean bootstrapOutdated(Path directory) throws IOException {
        var bootstrap = bootstrapFile(directory);
        if (!new ExtensionPreparationMarker(directory).exists() || !Files.isRegularFile(bootstrap)) {
            return false;
        }

        return !Files.readString(bootstrap).equals(ExtensionContextScript.bootstrap());
    }

    /** Writes this build's bootstrap and records its hash; the extension must be reloaded afterwards. */
    void updateBootstrap(Path directory) throws IOException {
        ExtensionFiles.writeAtomically(bootstrapFile(directory), ExtensionContextScript.bootstrap());
        new ExtensionPreparationMarker(directory).recordHash(ExtensionBootstrap.BOOTSTRAP_FILE);
        log.info("Updated the extension startup bootstrap: folder={}", directory);
    }

    void prepare(Path directory) throws IOException {
        var root = directory.toAbsolutePath().normalize();
        discardPreviousPreparation(root);

        var staging = root.getParent().resolve(ExtensionFiles.STAGING_PREFIX + UUID.randomUUID());
        var originals = root.getParent().getParent().resolve("extensions-originals")
                .resolve(root.getFileName() + "-" + UUID.randomUUID());
        try {
            new ExtensionBootstrap().prepareStartupFiles(root, staging);
            var changed = stagedChanges(root, staging);
            ExtensionFiles.copy(root, originals);
            commit(root, staging, originals, changed);
            log.info("Prepared extension startup adapter: folder={}, changedFiles={}, originals={}", root, changed, originals);
        } finally {
            ExtensionFiles.discardStaging(staging, root.getParent());
        }
    }

    private static Path bootstrapFile(Path directory) {
        return directory.resolve(ExtensionBootstrap.BOOTSTRAP_FILE);
    }

    private boolean hasBackground(Path directory) throws IOException {
        var background = JSON.readTree(directory.resolve("manifest.json").toFile()).path("background");

        return background.hasNonNull("service_worker") || background.hasNonNull("page") || background.path("scripts").isArray();
    }

    /** A new version copied over a prepared folder still contains the previous bootstrap file. */
    private void discardPreviousPreparation(Path root) throws IOException {
        var marker = new ExtensionPreparationMarker(root);
        if (marker.exists()) {
            Files.deleteIfExists(bootstrapFile(root));
            marker.delete();
        }
    }

    private List<String> stagedChanges(Path root, Path staging) throws IOException {
        var changed = new ArrayList<String>();
        try (var files = Files.walk(staging)) {
            for (var file : files.filter(Files::isRegularFile).toList()) {
                var relative = staging.relativize(file);
                var current = root.resolve(relative);
                if (!Files.exists(current) || Files.mismatch(file, current) != -1) {
                    changed.add(relative.toString().replace('\\', '/'));
                }
            }
        }

        return changed;
    }

    private void commit(Path root, Path staging, Path originals, List<String> changed) throws IOException {
        var hashes = new LinkedHashMap<String, String>();
        try {
            for (var relative : changed) {
                var target = ExtensionManifest.resolve(root, relative);
                ExtensionFiles.replace(ExtensionManifest.resolve(staging, relative), target);
                hashes.put(relative, ExtensionFingerprint.file(target));
            }
            new ExtensionPreparationMarker(root).write(originals.getFileName().toString(), hashes);
        } catch (IOException error) {
            rollback(root, originals, changed, error);
            throw error;
        }
    }

    private void rollback(Path root, Path originals, List<String> changed, IOException failure) {
        try {
            for (var relative : changed) {
                var original = ExtensionManifest.resolve(originals, relative);
                var target = ExtensionManifest.resolve(root, relative);
                if (Files.isRegularFile(original)) {
                    ExtensionFiles.replace(original, target);
                } else {
                    Files.deleteIfExists(target);
                }
            }
            new ExtensionPreparationMarker(root).delete();
        } catch (IOException error) {
            failure.addSuppressed(error);
        }
    }
}
