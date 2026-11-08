package to.sparkapp.app.extensions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Swaps an installed extension folder for a new version at the same path, keeping the old one as a backup. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ExtensionReplacement {
    static void replace(Path installed, Path updated) throws IOException {
        var backups = installed.getParent().getParent().resolve("extensions-removed");
        Files.createDirectories(backups);
        var backup = backups.resolve(installed.getFileName() + "-" + UUID.randomUUID());

        ExtensionFiles.moveWhenReady(installed, backup);
        try {
            ExtensionFiles.moveWhenReady(updated, installed);
        } catch (IOException error) {
            restore(backup, installed, error);
            throw error;
        }
    }

    private static void restore(Path backup, Path installed, IOException failure) {
        try {
            ExtensionFiles.moveWhenReady(backup, installed);
        } catch (IOException restoreError) {
            failure.addSuppressed(restoreError);
        }
    }
}
