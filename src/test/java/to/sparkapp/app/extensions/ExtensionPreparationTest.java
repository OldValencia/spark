package to.sparkapp.app.extensions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class ExtensionPreparationTest {
    @TempDir
    Path directory;

    @Test
    void preparationArchivesOriginalsRetainsTheInstalledPathAndIsIdempotent() throws Exception {
        var extension = fixture("worker.js");
        var original = Files.readString(extension.resolve("worker.js"));
        var asset = Files.createDirectories(extension.resolve("lib")).resolve("dependency.js");
        Files.writeString(asset, "const extensionDependency = 'unchanged';");
        var preparation = new ExtensionPreparation();
        assertTrue(preparation.needed(extension));
        preparation.prepare(extension);
        assertFalse(preparation.needed(extension));
        assertTrue(Files.readString(extension.resolve("worker.js")).contains("importScripts('/spark-context-bootstrap.js')"));
        try (var originals = Files.list(directory.resolve("extensions-originals"))) {
            var archive = originals.findFirst().orElseThrow();
            assertEquals(original, Files.readString(archive.resolve("worker.js")));
            assertFalse(Files.exists(archive.resolve("spark-context-bootstrap.js")));
            assertEquals(Files.readString(asset), Files.readString(archive.resolve("lib/dependency.js")));
        }
        var fingerprint = ExtensionFingerprint.of(extension);
        assertFalse(preparation.needed(extension));
        assertEquals(fingerprint, ExtensionFingerprint.of(extension));
    }

    @Test
    void invalidStartupPathsCannotModifyTheInstalledFolder() throws Exception {
        var extension = fixture("../outside.js");
        var preparation = new ExtensionPreparation();
        var fingerprint = ExtensionFingerprint.of(extension);
        assertThrows(IOException.class, () -> preparation.prepare(extension));
        assertEquals(fingerprint, ExtensionFingerprint.of(extension));
        try (var entries = Files.list(extension.getParent())) {
            assertFalse(entries.anyMatch(path -> path.getFileName().toString().startsWith(".spark-prepare-")));
        }
    }

    @Test
    void handEditedPreparedFilesAreReportedInsteadOfBeingPatchedAgain() throws Exception {
        var extension = fixture("worker.js");
        var preparation = new ExtensionPreparation();
        preparation.prepare(extension);
        var worker = extension.resolve("worker.js");
        Files.writeString(worker, Files.readString(worker) + "console.log('edited');");
        assertThrows(IOException.class, () -> preparation.needed(extension));
    }

    @Test
    void aNewVersionCopiedOverAPreparedFolderIsPreparedAgain() throws Exception {
        var extension = fixture("worker.js");
        var preparation = new ExtensionPreparation();
        preparation.prepare(extension);
        var worker = extension.resolve("worker.js");
        Files.writeString(worker, "console.log('replacement package');");

        assertTrue(preparation.needed(extension));
        preparation.prepare(extension);

        assertTrue(Files.readString(worker).contains("importScripts('/spark-context-bootstrap.js')"));
        assertTrue(Files.readString(worker).contains("replacement package"));
        assertFalse(preparation.needed(extension));
    }

    @Test
    void aBootstrapFromAnEarlierBuildIsReplacedWithoutPreparingAgain() throws Exception {
        var extension = fixture("worker.js");
        var preparation = new ExtensionPreparation();
        preparation.prepare(extension);
        var worker = Files.readString(extension.resolve("worker.js"));
        assertFalse(preparation.bootstrapOutdated(extension));

        Files.writeString(extension.resolve("spark-context-bootstrap.js"), "/* earlier build */");
        assertTrue(preparation.bootstrapOutdated(extension));
        preparation.updateBootstrap(extension);

        assertFalse(preparation.bootstrapOutdated(extension));
        assertFalse(preparation.needed(extension));
        assertEquals(worker, Files.readString(extension.resolve("worker.js")), "The worker must not be patched twice");
    }

    private Path fixture(String worker) throws Exception {
        var extension = Files.createDirectories(directory.resolve("extensions").resolve("fixture"));
        Files.writeString(extension.resolve("manifest.json"), """
                {"manifest_version":3,"name":"Preparation fixture","version":"1.0",
                 "background":{"service_worker":"%s"}}
                """.formatted(worker));
        Files.writeString(extension.resolve("worker.js"), "'use strict';\nchrome.runtime.onInstalled.addListener(()=>{});\n");
        return extension;
    }
}
