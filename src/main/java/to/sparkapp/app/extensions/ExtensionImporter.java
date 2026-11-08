package to.sparkapp.app.extensions;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.zip.ZipInputStream;

/** Imports ZIP or CRX into a stable directory without trusting archive paths. */
public final class ExtensionImporter {
    static final String UPDATE_PREFIX = ".spark-update-";
    private static final long MAX_UNPACKED_BYTES = 512L * 1024 * 1024;
    private static final int MAX_ENTRIES = 30_000;

    /** Unpacks into a new managed folder named after a random UUID. */
    public Path importArchive(Path archive, Path root) throws IOException {
        return importInto(archive, root, "extension-" + UUID.randomUUID());
    }

    /** Unpacks into a hidden folder that synchronization ignores, used to replace an existing installation. */
    Path importForUpdate(Path archive, Path root) throws IOException {
        return importInto(archive, root, UPDATE_PREFIX + UUID.randomUUID());
    }

    private Path importInto(Path archive, Path root, String folderName) throws IOException {
        Files.createDirectories(root);
        var staging = root.resolve(".import-" + UUID.randomUUID());
        Files.createDirectory(staging);
        try {
            CrxHeader header;
            try (var input = new DataInputStream(new BufferedInputStream(Files.newInputStream(archive)))) {
                header = CrxHeader.read(input);
                unpack(input, staging);
            }

            var directory = extensionRoot(staging);
            ExtensionManifest.read(directory);
            ExtensionSource.record(directory, header);
            var destination = root.resolve(folderName);
            ExtensionFiles.moveWhenReady(directory, destination);

            return destination;
        } finally {
            // The staging root itself may have become the installed directory.
            ExtensionFiles.deleteTree(staging);
        }
    }

    /** The shallowest folder containing a manifest; packages often wrap the extension in a top folder. */
    private Path extensionRoot(Path staging) throws IOException {
        try (var paths = Files.walk(staging)) {
            return paths.filter(path -> path.getFileName().toString().equals("manifest.json"))
                    .min(Comparator.comparingInt(Path::getNameCount))
                    .map(Path::getParent)
                    .orElseThrow(() -> new IOException("The archive does not contain an extension manifest.json"));
        }
    }

    private void unpack(DataInputStream input, Path staging) throws IOException {
        var total = 0L;
        var entries = 0;
        var buffer = new byte[8192];
        try (var zip = new ZipInputStream(input)) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (++entries > MAX_ENTRIES) {
                    throw new IOException("The archive contains too many files");
                }
                var destination = ExtensionManifest.resolve(staging, entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(destination);
                    continue;
                }
                Files.createDirectories(destination.getParent());
                try (var output = Files.newOutputStream(destination)) {
                    var read = zip.read(buffer);
                    while (read != -1) {
                        total += read;
                        if (total > MAX_UNPACKED_BYTES) {
                            throw new IOException("The unpacked extension is too large");
                        }
                        output.write(buffer, 0, read);
                        read = zip.read(buffer);
                    }
                }
            }
        }
    }
}
