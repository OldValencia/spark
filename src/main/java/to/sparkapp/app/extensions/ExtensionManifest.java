package to.sparkapp.app.extensions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** The parts of an extension manifest that Spark shows or opens; page paths are validated to stay inside the folder. */
public record ExtensionManifest(Path directory, String name, String version, Path icon, String popup, String options) {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long MAX_JSON_BYTES = 1024 * 1024;
    private static final long MAX_ICON_BYTES = 4 * 1024 * 1024;

    public static ExtensionManifest read(Path directory) throws IOException {
        var file = directory.resolve("manifest.json");
        if (Files.size(file) > MAX_JSON_BYTES) {
            throw new IOException("The extension manifest is too large");
        }
        var manifest = JSON.readTree(file.toFile());
        var name = localizedName(directory, manifest);
        var action = action(manifest);
        var options = manifest.path("options_ui").path("page").asText(manifest.path("options_page").asText(""));
        var icons = manifest.path("icons");
        if (icons.isMissingNode()) {
            icons = action.path("default_icon");
        }
        var icon = iconPath(directory, icons);
        var popup = action.path("default_popup").asText("");
        validatePage(directory, popup);
        validatePage(directory, options);

        return new ExtensionManifest(directory, name, manifest.path("version").asText(""), icon, popup, options);
    }

    /** MV3 uses "action"; MV2 declares either a browser or a page action. */
    private static JsonNode action(JsonNode manifest) {
        for (var key : new String[]{"action", "browser_action", "page_action"}) {
            var action = manifest.path(key);
            if (!action.isMissingNode()) {
                return action;
            }
        }

        return manifest.path("action");
    }

    /** Describes a folder whose manifest could not be read, so its error can still be shown. */
    static ExtensionManifest placeholder(Path directory) {
        return new ExtensionManifest(directory, directory.getFileName().toString(), "", null, "", "");
    }

    public static Path resolve(Path directory, String relative) throws IOException {
        var root = directory.toAbsolutePath().normalize();
        var path = root.resolve(relative).normalize();
        if (!path.startsWith(root) || relative.contains(":") || relative.startsWith("/") || relative.contains("\\")) {
            throw new IOException("An extension path points outside its folder");
        }

        return path;
    }

    private static void validatePage(Path directory, String relative) throws IOException {
        if (!relative.isBlank()) {
            resolve(directory, relative);
        }
    }

    private static String localizedName(Path directory, JsonNode manifest) throws IOException {
        var name = manifest.path("name").asText(directory.getFileName().toString());
        if (name.startsWith("__MSG_") && name.endsWith("__")) {
            var locale = manifest.path("default_locale").asText("en");
            var messages = resolve(directory, "_locales/" + locale + "/messages.json");
            if (Files.isRegularFile(messages) && Files.size(messages) < MAX_JSON_BYTES) {
                name = JSON.readTree(messages.toFile()).path(name.substring(6, name.length() - 2)).path("message").asText(name);
            }
        }

        return name;
    }

    private static Path iconPath(Path directory, JsonNode icons) throws IOException {
        String relative = null;
        if (icons.isTextual()) {
            relative = icons.asText();
        } else if (icons.isObject()) {
            var sizes = new ArrayList<String>();
            icons.fieldNames().forEachRemaining(sizes::add);
            relative = sizes.stream().filter(size -> size.matches("[0-9]{1,4}"))
                    .max(Comparator.comparingInt(Integer::parseInt)).map(size -> icons.path(size).asText()).orElse(null);
        }
        if (relative == null || relative.isBlank()) {
            return null;
        }
        var icon = resolve(directory, relative);

        return Files.isRegularFile(icon) && Files.size(icon) < MAX_ICON_BYTES ? icon : null;
    }
}
