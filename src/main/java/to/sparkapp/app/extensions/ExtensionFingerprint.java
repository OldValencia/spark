package to.sparkapp.app.extensions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Detects changed extension folders from file metadata, so unchanged folders are never read in full.
 * Folders recorded before metadata fingerprints existed are verified once by content.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ExtensionFingerprint {
    private static final String METADATA_PREFIX = "meta:";

    static String of(Path directory) throws IOException {
        var digest = sha256();
        try (var paths = Files.walk(directory)) {
            for (var path : paths.sorted().toList()) {
                var attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                rejectSymbolicLink(attributes);
                if (attributes.isRegularFile()) {
                    var entry = directory.relativize(path) + "|" + attributes.size() + "|"
                            + attributes.lastModifiedTime().toMillis() + "\n";
                    digest.update(entry.getBytes(StandardCharsets.UTF_8));
                }
            }
        }

        return METADATA_PREFIX + HexFormat.of().formatHex(digest.digest());
    }

    /** Accepts the current metadata fingerprint, or a legacy content fingerprint that still matches. */
    static boolean matches(Path directory, String current, String recorded) throws IOException {
        if (recorded == null || recorded.isBlank()) {
            return false;
        }
        if (recorded.startsWith(METADATA_PREFIX)) {
            return recorded.equals(current);
        }

        return recorded.equals(content(directory));
    }

    static String content(Path directory) throws IOException {
        var digest = sha256();
        try (var paths = Files.walk(directory)) {
            for (var path : paths.sorted().toList()) {
                var attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                rejectSymbolicLink(attributes);
                if (attributes.isRegularFile()) {
                    digest.update(directory.relativize(path).toString().getBytes(StandardCharsets.UTF_8));
                    update(digest, path);
                }
            }
        }

        return HexFormat.of().formatHex(digest.digest());
    }

    static String file(Path file) throws IOException {
        var digest = sha256();
        update(digest, file);

        return HexFormat.of().formatHex(digest.digest());
    }

    private static void update(MessageDigest digest, Path file) throws IOException {
        try (var input = Files.newInputStream(file)) {
            var buffer = new byte[8192];
            var count = input.read(buffer);
            while (count != -1) {
                digest.update(buffer, 0, count);
                count = input.read(buffer);
            }
        }
    }

    private static void rejectSymbolicLink(BasicFileAttributes attributes) throws IOException {
        if (attributes.isSymbolicLink() || attributes.isOther()) {
            throw new IOException("Extension folders cannot contain symbolic links");
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}
