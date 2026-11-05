package to.sparkapp.app.browser;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Immutable native binaries shared safely by app and test processes. */
final class NativeLibraryCache {
    private NativeLibraryCache() {}

    static synchronized Path extract(Path cacheRoot, Path relativeLibrary, byte[] content) throws IOException {
        var root = cacheRoot.toAbsolutePath().normalize();
        var library = root.resolve(relativeLibrary).normalize();
        if (relativeLibrary.isAbsolute() || !library.startsWith(root) || library.equals(root)) {
            throw new IOException("Native library path must stay inside its cache");
        }
        String hash = digest(content);
        var directory = library.getParent().resolve(hash);
        var target = directory.resolve(library.getFileName());
        Files.createDirectories(directory);
        // Avoid replacing a DLL already loaded by another process. The lock also
        // serializes first extraction across independent JVMs; synchronized covers this JVM.
        try (var channel = FileChannel.open(directory.resolve("extract.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var lock = channel.lock()) {
            if (Files.exists(target)) {
                if (!digest(Files.readAllBytes(target)).equals(hash)) {
                    throw new IOException("Native library cache content mismatch: " + target);
                }
                return target;
            }
            var staging = Files.createTempFile(directory, "extract-", ".tmp");
            try {
                Files.write(staging, content);
                Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(staging);
            }
            return target;
        }
    }

    private static String digest(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", impossible);
        }
    }
}
