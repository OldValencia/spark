package to.sparkapp.app.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Replaces complete JSON files and retains the last readable configuration.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class JsonFiles {
    static <T> T read(File file, ObjectReader reader) throws IOException {
        try {
            return reader.readValue(file);
        } catch (IOException failure) {
            try {
                return reader.readValue(backup(file.toPath()).toFile());
            } catch (IOException backupFailure) {
                failure.addSuppressed(backupFailure);
                throw failure;
            }
        }
    }

    static void write(File file, ObjectMapper mapper, Object value) throws IOException {
        // Serialize first: a failed serializer must leave both files untouched.
        byte[] json = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(value);
        Path target = file.toPath().toAbsolutePath();
        Files.createDirectories(target.getParent());

        if (Files.exists(target)) {
            byte[] previous = Files.readAllBytes(target);
            boolean readable;

            try {
                readable = mapper.readTree(previous) != null;
            } catch (IOException invalid) {
                readable = false;
            }

            if (readable) {
                replace(backup(target), previous);
            }
        }

        replace(target, json);
    }

    private static Path backup(Path target) {
        return target.resolveSibling(target.getFileName() + ".bak");
    }

    private static void replace(Path target, byte[] bytes) throws IOException {
        Path temporary = Files.createTempFile(target.getParent(), "spark-config-", ".tmp");

        try {
            Files.write(temporary, bytes);

            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
