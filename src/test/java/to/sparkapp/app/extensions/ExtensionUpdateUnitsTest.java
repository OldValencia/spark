package to.sparkapp.app.extensions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExtensionUpdateUnitsTest {
    private static final Path SAMPLES = Path.of("local", "extensions-samples");

    @TempDir
    Path directory;

    @Test
    void storeIdsAreReadFromCrxHeaders() throws Exception {
        assumeTrue(Files.isRegularFile(SAMPLES.resolve("darkreader.crx")), "Optional sample packages are absent");
        assertEquals("eimadpbcbfnmbkopoojfekhnkhdbieeh", storeId(SAMPLES.resolve("darkreader.crx")));
        assertEquals("fihnjjcciajhdojfnbdddfaoknhalnja", storeId(SAMPLES.resolve("cookies.crx")));
    }

    @Test
    void zipArchivesHaveNoCrxHeaderAndStayReadable() throws Exception {
        var input = new DataInputStream(new BufferedInputStream(new ByteArrayInputStream(new byte[]{'P', 'K', 3, 4, 0})));
        assertNull(CrxHeader.read(input));
        assertEquals('P', input.read(), "A non-CRX stream must be left at its first byte");
    }

    @Test
    void importedStorePackagesRecordTheirUpdateService() throws Exception {
        assumeTrue(Files.isRegularFile(SAMPLES.resolve("darkreader.crx")), "Optional sample packages are absent");
        var imported = new ExtensionImporter().importArchive(SAMPLES.resolve("darkreader.crx"), directory);
        var source = ExtensionSource.read(imported).orElseThrow();
        assertEquals("eimadpbcbfnmbkopoojfekhnkhdbieeh", source.storeId());
        assertEquals("https://clients2.google.com/service/update2/crx", source.updateUrl());
        assertTrue(source.updatable());
    }

    @Test
    void versionsCompareNumericallyPartByPart() {
        assertTrue(ExtensionVersion.isNewer("4.9.140", "4.9.135"));
        assertTrue(ExtensionVersion.isNewer("1.10", "1.9.9"));
        assertTrue(ExtensionVersion.isNewer("2026.11.0", "2026.10.2"));
        assertFalse(ExtensionVersion.isNewer("1.0", "1.0.0"));
        assertFalse(ExtensionVersion.isNewer("1.0", "1.0.1"));
        assertFalse(ExtensionVersion.isNewer("", "1.0"));
    }

    @Test
    void storeIdsAreRecognizedInLinksAndBareIds() {
        var id = "eimadpbcbfnmbkopoojfekhnkhdbieeh";
        assertEquals(id, ChromeWebStore.extensionId(id).orElseThrow());
        assertEquals(id, ChromeWebStore.extensionId(" " + id.toUpperCase() + " ").orElseThrow());
        assertEquals(id, ChromeWebStore.extensionId("https://chromewebstore.google.com/detail/dark-reader/" + id + "?hl=en").orElseThrow());
        assertEquals(id, ChromeWebStore.extensionId("https://chrome.google.com/webstore/detail/dark-reader/" + id).orElseThrow());
        assertTrue(ChromeWebStore.extensionId("https://chromewebstore.google.com/detail/dark-reader").isEmpty());
        assertTrue(ChromeWebStore.extensionId(id.substring(1)).isEmpty());
        assertTrue(ChromeWebStore.extensionId(id + "a").isEmpty(), "A longer letter run is not an ID");
    }

    private static String storeId(Path crx) throws Exception {
        try (var input = new DataInputStream(new BufferedInputStream(Files.newInputStream(crx)))) {
            return CrxHeader.read(input).storeId();
        }
    }
}
