package to.sparkapp.app.config;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonFilesTest {
    @TempDir
    Path directory;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void recoversLastReadableConfigurationAfterCorruption() throws Exception {
        var file = directory.resolve("settings.json").toFile();
        JsonFiles.write(file, mapper, Map.of("version", 1));
        JsonFiles.write(file, mapper, Map.of("version", 2));
        Files.writeString(file.toPath(), "{broken");

        Map<?, ?> restored = JsonFiles.read(file, mapper.readerFor(Map.class));
        assertEquals(1, restored.get("version"));
        JsonFiles.write(file, mapper, Map.of("version", 3));
        assertEquals(1, mapper.readTree(directory.resolve("settings.json.bak").toFile()).get("version").asInt());
    }

    @Test
    void failedSerializationPreservesExistingConfiguration() throws Exception {
        var file = directory.resolve("settings.json").toFile();
        JsonFiles.write(file, mapper, Map.of("value", "original"));

        String original = Files.readString(file.toPath());
        assertThrows(Exception.class, () -> JsonFiles.write(file, mapper, new Object()));
        assertEquals(original, Files.readString(file.toPath()));

        try (var files = Files.list(directory)) {
            assertEquals(1, files.count());
        }
    }
}
