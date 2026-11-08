package to.sparkapp.app.extensions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Remembers which browser extension IDs Spark installed. The profile also lists built-in WebView2
 * components, which must never be treated as stale user extensions.
 */
final class ExtensionInstallations {
    private static final String FILE = ".spark-installed.json";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Path root;
    private final Set<String> ids;

    /** Seeds the list from the folder index the first time, so earlier installations are covered. */
    ExtensionInstallations(Path root, Collection<String> indexedIds) throws IOException {
        this.root = root;
        var file = root.resolve(FILE);
        if (Files.isRegularFile(file)) {
            ids = JSON.readValue(file.toFile(), new TypeReference<TreeSet<String>>() {});
        } else {
            ids = new TreeSet<>(indexedIds);
            save();
        }
    }

    boolean contains(String id) {
        return ids.contains(id);
    }

    void add(String id) throws IOException {
        if (id != null && ids.add(id)) {
            save();
        }
    }

    void remove(String id) throws IOException {
        if (ids.remove(id)) {
            save();
        }
    }

    private void save() throws IOException {
        ExtensionFiles.writeAtomically(root.resolve(FILE), JSON.writeValueAsString(ids));
    }
}
