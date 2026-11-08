package to.sparkapp.app.extensions;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExtensionImporterTest {
    @TempDir
    Path directory;

    @Test
    void importsNestedCrx3AndReadsCommonPopupAndSettingsPaths() throws Exception {
        var archive = packageBytes("nested/manifest.json", """
                {"manifest_version":3,"name":"Test","version":"1.2","action":{"default_popup":"popup.html"},
                 "options_ui":{"page":"options.html"}}
                """);
        var crx = directory.resolve("extension.crx");
        try (var output = new DataOutputStream(Files.newOutputStream(crx))) {
            output.writeInt(0x43723234);
            output.writeInt(Integer.reverseBytes(3));
            output.writeInt(Integer.reverseBytes(4));
            output.writeInt(0);
            output.write(archive);
        }
        var imported = new ExtensionImporter().importArchive(crx, directory.resolve("extensions"));
        var manifest = ExtensionManifest.read(imported);
        assertEquals("Test", manifest.name());
        assertEquals("popup.html", manifest.popup());
        assertEquals("options.html", manifest.options());
        assertEquals("1.2", manifest.version());
    }

    @Test
    void rejectsArchiveTraversalAndLeavesNoInstalledDirectory() throws Exception {
        var archive = directory.resolve("bad.zip");
        Files.write(archive, packageBytes("../outside.txt", "bad"));
        var root = directory.resolve("extensions");
        assertThrows(Exception.class, () -> new ExtensionImporter().importArchive(archive, root));
        assertFalse(Files.exists(directory.resolve("outside.txt")));
        try (var children = Files.list(root)) {
            assertEquals(0, children.count());
        }
    }

    @Test
    void rejectsExternalManifestPagesAndPreservesIndependentState() throws Exception {
        assertThrows(Exception.class, () -> ExtensionManifest.resolve(directory, "../secret"));
        assertThrows(Exception.class, () -> ExtensionManifest.resolve(directory, "C:/secret"));
        var root = directory.resolve("extensions");
        var store = new ExtensionStore(root);
        var folder = Files.createDirectory(root.resolve("test"));
        Files.writeString(folder.resolve("manifest.json"), "{}");
        var fingerprint = ExtensionFingerprint.of(folder);
        store.put(folder, new ExtensionStore.Saved("id", fingerprint, false));
        assertFalse(new ExtensionStore(root).get(folder).enabled());
        Files.writeString(folder.resolve("manifest.json"), "{\"name\":\"changed\"}");
        assertNotEquals(fingerprint, ExtensionFingerprint.of(folder));
        store.remove(folder);
        assertFalse(Files.exists(folder));
        assertNull(new ExtensionStore(root).get(folder));
        try (var moved = Files.list(directory.resolve("extensions-removed"))) {
            assertEquals(1, moved.count());
        }
    }

    private byte[] packageBytes(String path, String content) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry(path));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }
}
