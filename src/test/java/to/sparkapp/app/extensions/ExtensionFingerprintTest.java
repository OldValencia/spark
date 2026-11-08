package to.sparkapp.app.extensions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExtensionFingerprintTest {
    @TempDir
    Path directory;

    @Test
    void metadataFingerprintDetectsChangedAddedAndTouchedFiles() throws Exception {
        var script = directory.resolve("script.js");
        Files.writeString(script, "one");
        var initial = ExtensionFingerprint.of(directory);
        assertEquals(initial, ExtensionFingerprint.of(directory));

        Files.writeString(script, "two");
        Files.setLastModifiedTime(script, FileTime.fromMillis(1_000));
        var rewritten = ExtensionFingerprint.of(directory);
        assertNotEquals(initial, rewritten);

        Files.setLastModifiedTime(script, FileTime.fromMillis(2_000));
        assertNotEquals(rewritten, ExtensionFingerprint.of(directory));

        Files.writeString(directory.resolve("added.js"), "");
        assertNotEquals(rewritten, ExtensionFingerprint.of(directory));
    }

    @Test
    void legacyContentFingerprintsAreAcceptedOnlyWhileContentMatches() throws Exception {
        Files.writeString(directory.resolve("manifest.json"), "{}");
        var legacy = ExtensionFingerprint.content(directory);
        var current = ExtensionFingerprint.of(directory);
        assertTrue(ExtensionFingerprint.matches(directory, current, legacy));
        assertTrue(ExtensionFingerprint.matches(directory, current, current));
        assertFalse(ExtensionFingerprint.matches(directory, current, ""));

        Files.writeString(directory.resolve("manifest.json"), "{\"name\":\"changed\"}");
        assertFalse(ExtensionFingerprint.matches(directory, ExtensionFingerprint.of(directory), legacy));
        assertFalse(ExtensionFingerprint.matches(directory, ExtensionFingerprint.of(directory), current));
    }

    @Test
    void storeReportsOnlyIdsOfExistingFoldersAndForgetsMissingOnes() throws Exception {
        var store = new ExtensionStore(directory.resolve("extensions"));
        var kept = Files.createDirectory(store.root().resolve("kept"));
        Files.writeString(kept.resolve("manifest.json"), "{}");
        var deleted = store.root().resolve("deleted");
        store.put(kept, new ExtensionStore.Saved("kept-id", "", true));
        store.put(deleted, new ExtensionStore.Saved("deleted-id", "", true));

        var directories = store.availableDirectories();
        assertEquals(List.of(kept), directories);
        assertEquals(Set.of("kept-id"), store.knownIds(directories));

        store.forgetMissing(directories);
        var reopened = new ExtensionStore(store.root());
        assertEquals("kept-id", reopened.get(kept).id());
        assertEquals(null, reopened.get(deleted));
    }
}
