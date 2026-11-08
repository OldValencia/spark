package to.sparkapp.app.extensions;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** File operations for managed extension folders; replacements never leave a half-written target. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ExtensionFiles {
    static final String STAGING_PREFIX = ".spark-prepare-";
    private static final int MOVE_ATTEMPTS = 6;
    private static final long MOVE_RETRY_MILLIS = 50;

    static void replace(Path source, Path target) throws IOException {
        var temporary = Files.createTempFile(target.getParent(), ".spark-replace-", ".tmp");
        try {
            Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** Moves a folder, retrying briefly while Windows scanners or the browser release its files. */
    static void moveWhenReady(Path source, Path destination) throws IOException {
        for (var attempt = 0; ; attempt++) {
            try {
                Files.move(source, destination);
                return;
            } catch (AccessDeniedException error) {
                if (attempt == MOVE_ATTEMPTS - 1) {
                    throw error;
                }
                pause(MOVE_RETRY_MILLIS << attempt);
            }
        }
    }

    private static void pause(long millis) throws IOException {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("The extension file operation was interrupted", interrupted);
        }
    }

    static void writeAtomically(Path target, String content) throws IOException {
        var temporary = Files.createTempFile(target.getParent(), ".spark-write-", ".tmp");
        try {
            Files.writeString(temporary, content);
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** Copies a folder into a new directory; symbolic links are rejected rather than followed. */
    static void copy(Path source, Path destination) throws IOException {
        var root = source.toAbsolutePath().normalize();
        var targetRoot = destination.toAbsolutePath().normalize();
        if (targetRoot.startsWith(root) || Files.exists(targetRoot) || Files.isSymbolicLink(targetRoot)) {
            throw new IOException("The extension copy must use a separate, new directory");
        }

        try (var paths = Files.walk(root)) {
            for (var path : paths.toList()) {
                if (Files.isSymbolicLink(path)) {
                    throw new IOException("Extension folders cannot contain symbolic links");
                }
                var target = targetRoot.resolve(root.relativize(path));
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(path, target);
                }
            }
        }
    }

    /** Deletes a preparation staging folder, refusing anything outside the extension workspace. */
    static void discardStaging(Path directory, Path parent) throws IOException {
        var insideWorkspace = directory.toAbsolutePath().normalize().getParent().equals(parent.toAbsolutePath().normalize());
        if (!insideWorkspace || !directory.getFileName().toString().startsWith(STAGING_PREFIX) || Files.isSymbolicLink(directory)) {
            throw new IOException("The staging folder is outside the extension workspace");
        }

        deleteTree(directory);
    }

    static void deleteTree(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }

        try (var paths = Files.walk(directory)) {
            for (var path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
