package to.sparkapp.app.browser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class NativeLibraryCacheTest {
    @TempDir Path cache;
    private static final Path LIBRARY = Path.of("x86_64", "windows_nt", "webview.dll");
    private static final byte[] CONTENT = "test native binary".getBytes(StandardCharsets.UTF_8);

    @Test
    void reusesExistingBinaryWithoutRewritingIt() throws Exception {
        var first = NativeLibraryCache.extract(cache, LIBRARY, CONTENT);
        var timestamp = FileTime.fromMillis(1_600_000_000_000L);
        Files.setLastModifiedTime(first, timestamp);
        try (var open = FileChannel.open(first, StandardOpenOption.READ)) {
            assertEquals(first, NativeLibraryCache.extract(cache, LIBRARY, CONTENT));
            assertTrue(open.isOpen());
            assertEquals(timestamp, Files.getLastModifiedTime(first));
        }
        assertArrayEquals(CONTENT, Files.readAllBytes(first));
    }

    @Test
    void separatesVersionsAndArchitectures() throws Exception {
        var first = NativeLibraryCache.extract(cache, LIBRARY, CONTENT);
        var updated = NativeLibraryCache.extract(cache, LIBRARY, new byte[]{1, 2, 3});
        var otherArch = NativeLibraryCache.extract(cache, Path.of("aarch64", "macos", "libwebview.dylib"), CONTENT);
        assertNotEquals(first, updated);
        assertNotEquals(first, otherArch);
        assertArrayEquals(CONTENT, Files.readAllBytes(first));
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(updated));
    }

    @Test
    void rejectsCorruptCacheWithoutSilentlyLoadingOrOverwritingIt() throws Exception {
        var target = NativeLibraryCache.extract(cache, LIBRARY, CONTENT);
        Files.writeString(target, "corrupt");
        assertThrows(IOException.class, () -> NativeLibraryCache.extract(cache, LIBRARY, CONTENT));
        assertEquals("corrupt", Files.readString(target));
    }

    @Test
    void concurrentExtractionPublishesOneCompleteBinaryAndNoStagingFiles() throws Exception {
        var tasks = new ArrayList<CompletableFuture<Path>>();
        for (int i = 0; i < 8; i++) {
            tasks.add(CompletableFuture.supplyAsync(() -> {
                try { return NativeLibraryCache.extract(cache, LIBRARY, CONTENT); }
                catch (IOException failure) { throw new IllegalStateException(failure); }
            }));
        }
        var target = tasks.getFirst().get(5, TimeUnit.SECONDS);
        for (var task : tasks) assertEquals(target, task.get(5, TimeUnit.SECONDS));
        assertArrayEquals(CONTENT, Files.readAllBytes(target));
        try (var files = Files.list(target.getParent())) {
            assertEquals(2, files.count(), "Only the binary and extraction lock should remain");
        }
    }

    @Test
    void rejectsPathsOutsideCache() {
        assertThrows(IOException.class, () -> NativeLibraryCache.extract(cache, Path.of("..", "outside.dll"), CONTENT));
        assertThrows(IOException.class, () -> NativeLibraryCache.extract(cache, cache.resolve("absolute.dll"), CONTENT));
    }
}
