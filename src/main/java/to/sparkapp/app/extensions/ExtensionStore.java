package to.sparkapp.app.extensions;

import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import to.sparkapp.app.config.AppPaths;

/** Persists which managed folder maps to which browser extension and its saved state. */
public final class ExtensionStore {
    private static final String INDEX = ".spark-index.json";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Path root;
    private final Map<String, Saved> saved;
    private final ExtensionInstallations installations;

    public ExtensionStore() throws IOException {
        this(AppPaths.DATA_DIR.toPath().resolve("extensions"));
    }

    public ExtensionStore(Path root) throws IOException {
        this.root = root.toAbsolutePath().normalize();
        Files.createDirectories(this.root);

        var index = this.root.resolve(INDEX);
        saved = Files.isRegularFile(index) ? JSON.readValue(index.toFile(), new TypeReference<>() {}) : new HashMap<>();
        var indexedIds = saved.values().stream().map(Saved::id).filter(Objects::nonNull).toList();
        installations = new ExtensionInstallations(this.root, indexedIds);
    }

    public Path root() {
        return root;
    }

    /** Lists managed extension folders and retries archiving folders that were removed while locked. */
    List<Path> availableDirectories() throws IOException {
        var available = new ArrayList<Path>();
        for (var directory : extensionDirectories()) {
            var state = get(directory);
            if (state != null && state.removed()) {
                remove(directory);
            } else {
                available.add(directory);
            }
        }

        return List.copyOf(available);
    }

    /** True when Spark installed this ID; built-in WebView2 components are never managed. */
    boolean isManaged(String id) {
        return installations.contains(id);
    }

    void forgetInstallation(String id) throws IOException {
        installations.remove(id);
    }

    /** Browser IDs that belong to existing folders; other managed IDs in the profile are stale. */
    Set<String> knownIds(List<Path> directories) {
        var ids = new HashSet<String>();
        for (var directory : directories) {
            var state = get(directory);
            if (state != null && state.id() != null && !state.removed()) {
                ids.add(state.id());
            }
        }

        return ids;
    }

    /** Drops index entries whose folders no longer exist. */
    void forgetMissing(List<Path> directories) throws IOException {
        var present = new HashSet<String>();
        for (var directory : directories) {
            present.add(directory.getFileName().toString());
        }

        var changed = saved.keySet().removeIf(name -> !present.contains(name) && !Files.isDirectory(root.resolve(name)));
        if (changed) {
            save();
        }
    }

    Saved get(Path directory) {
        return saved.get(directory.getFileName().toString());
    }

    void put(Path directory, Saved state) throws IOException {
        var previous = saved.put(directory.getFileName().toString(), state);
        if (!Objects.equals(previous, state)) {
            save();
        }
        installations.add(state.id());
    }

    void remove(Path directory) throws IOException {
        var normalized = directory.toAbsolutePath().normalize();
        if (!root.equals(normalized.getParent()) || Files.isSymbolicLink(normalized)) {
            throw new IOException("The extension is outside the managed folder");
        }

        var removed = root.getParent().resolve("extensions-removed");
        Files.createDirectories(removed);

        var name = directory.getFileName().toString();
        var state = saved.get(name);
        saved.put(name, new Saved(state == null ? null : state.id(), "", false, true));
        save();

        try {
            Files.move(normalized, removed.resolve(UUID.randomUUID().toString()));
            saved.remove(name);
            save();
        } catch (FileSystemException ignored) {
            // WebView2 may still hold files; the tombstone prevents reinstallation on restart.
        }
    }

    private List<Path> extensionDirectories() throws IOException {
        try (var paths = Files.list(root)) {
            return paths.filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> !path.getFileName().toString().startsWith("."))
                    .filter(path -> Files.isRegularFile(path.resolve("manifest.json")))
                    .sorted()
                    .toList();
        }
    }

    private void save() throws IOException {
        ExtensionFiles.writeAtomically(root.resolve(INDEX), JSON.writeValueAsString(saved));
    }

    record Saved(String id, String fingerprint, boolean enabled, boolean removed) {
        Saved(String id, String fingerprint, boolean enabled) {
            this(id, fingerprint, enabled, false);
        }

        Saved withEnabled(boolean value) {
            return new Saved(id, fingerprint, value, removed);
        }
    }
}
