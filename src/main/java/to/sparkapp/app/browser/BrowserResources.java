package to.sparkapp.app.browser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Reads scripts bundled with the application; a missing resource is a packaging error. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class BrowserResources {
    static String text(String path) {
        try (var stream = BrowserResources.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("The bundled resource is missing: " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new UncheckedIOException("Could not read the bundled resource: " + path, error);
        }
    }
}
