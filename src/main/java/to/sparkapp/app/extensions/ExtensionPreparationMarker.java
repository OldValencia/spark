package to.sparkapp.app.extensions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** The marker file that records which startup files Spark changed, with their hashes. */
final class ExtensionPreparationMarker {
    static final String FILE = ".spark-bootstrap.json";
    private static final int VERSION = 1;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Path directory;
    private final Path file;

    ExtensionPreparationMarker(Path directory) {
        this.directory = directory;
        file = directory.resolve(FILE);
    }

    boolean exists() {
        return Files.isRegularFile(file);
    }

    /** Recorded files whose current bytes differ from the recorded hash, or that are missing. */
    List<Path> changedFiles() throws IOException {
        var changed = new ArrayList<Path>();
        for (var entry : files(read()).properties()) {
            var path = ExtensionManifest.resolve(directory, entry.getKey());
            if (!Files.isRegularFile(path) || !ExtensionFingerprint.file(path).equals(entry.getValue().asText())) {
                changed.add(path);
            }
        }

        return changed;
    }

    void write(String originals, Map<String, String> hashes) throws IOException {
        var state = JSON.createObjectNode();
        state.put("version", VERSION);
        state.put("originals", originals);
        var files = state.putObject("files");
        hashes.forEach(files::put);
        save(state);
    }

    /** Records the current hash of one file that Spark rewrote. */
    void recordHash(String relative) throws IOException {
        var state = read();
        var hash = ExtensionFingerprint.file(ExtensionManifest.resolve(directory, relative));
        files(state).put(relative, hash);
        save(state);
    }

    void delete() throws IOException {
        Files.deleteIfExists(file);
    }

    private ObjectNode read() throws IOException {
        if (!(JSON.readTree(file.toFile()) instanceof ObjectNode state) || state.path("version").asInt() != VERSION) {
            throw invalid();
        }

        return state;
    }

    private static ObjectNode files(ObjectNode state) throws IOException {
        if (!(state.path("files") instanceof ObjectNode files)) {
            throw invalid();
        }

        return files;
    }

    private void save(ObjectNode state) throws IOException {
        ExtensionFiles.writeAtomically(file, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(state));
    }

    private static IOException invalid() {
        return new IOException("The extension compatibility metadata is invalid. Reimport the package.");
    }
}
