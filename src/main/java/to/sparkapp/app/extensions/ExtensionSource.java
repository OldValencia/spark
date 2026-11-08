package to.sparkapp.app.extensions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Where an imported package came from: its store ID and the update service declared in its manifest.
 * Packages without an update service, such as release archives, are updated by importing them again.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ExtensionSource(String storeId, String updateUrl) {
    static final String FILE = ".spark-source.json";
    private static final ObjectMapper JSON = new ObjectMapper();

    static Optional<ExtensionSource> read(Path directory) {
        var file = directory.resolve(FILE);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }

        try {
            return Optional.of(JSON.readValue(file.toFile(), ExtensionSource.class));
        } catch (IOException error) {
            return Optional.empty();
        }
    }

    /** Records the source of a freshly unpacked CRX; the manifest supplies the update service. */
    static void record(Path directory, CrxHeader header) throws IOException {
        if (header == null || header.storeId() == null) {
            return;
        }

        var updateUrl = JSON.readTree(directory.resolve("manifest.json").toFile()).path("update_url").asText("");
        var source = new ExtensionSource(header.storeId(), updateUrl.isBlank() ? null : updateUrl);
        Files.writeString(directory.resolve(FILE), JSON.writeValueAsString(source));
    }

    boolean updatable() {
        return storeId != null && updateUrl != null && updateUrl.startsWith("https://");
    }
}
