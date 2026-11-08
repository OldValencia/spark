package to.sparkapp.app.extensions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import to.sparkapp.app.browser.ExtensionContextScript;

/** Prepares a separate unpacked copy before any background code can capture browser APIs. */
public final class ExtensionBootstrap {
    static final String BOOTSTRAP_FILE = "spark-context-bootstrap.js";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern HEAD = Pattern.compile("<head(?:\\s[^>]*)?>", Pattern.CASE_INSENSITIVE);

    public void prepareCopy(Path source, Path destination) throws IOException {
        ExtensionManifest.read(source);
        ExtensionFiles.copy(source, destination);
        prepare(destination);
    }

    void prepareStartupFiles(Path source, Path destination) throws IOException {
        ExtensionManifest.read(source);
        if (Files.exists(source.resolve(BOOTSTRAP_FILE))) {
            throw new IOException("The extension already contains the reserved Spark bootstrap filename");
        }
        Files.createDirectory(destination);
        Files.copy(source.resolve("manifest.json"), destination.resolve("manifest.json"));
        var background = JSON.readTree(source.resolve("manifest.json").toFile()).path("background");
        var entry = background.path("service_worker").asText(background.path("page").asText(""));
        if (!entry.isBlank()) {
            var original = ExtensionManifest.resolve(source, entry);
            var target = ExtensionManifest.resolve(destination, entry);
            Files.createDirectories(target.getParent());
            Files.copy(original, target);
        }
        prepare(destination);
    }

    public void prepare(Path directory) throws IOException {
        var manifestFile = directory.resolve("manifest.json");
        var manifest = (ObjectNode) JSON.readTree(manifestFile.toFile());
        var background = manifest.path("background");
        if (!background.isObject() || background.isEmpty()) {
            return;
        }

        var bootstrap = directory.resolve(BOOTSTRAP_FILE);
        if (Files.exists(bootstrap)) {
            throw new IOException("The extension already contains the reserved Spark bootstrap filename");
        }
        Files.writeString(bootstrap, ExtensionContextScript.bootstrap());

        if (background.hasNonNull("service_worker")) {
            prepareWorker(directory, background);
        } else if (background.hasNonNull("page")) {
            preparePage(directory, background.path("page").asText());
        } else if (background.path("scripts").isArray()) {
            ((ObjectNode) background).withArray("scripts").insert(0, BOOTSTRAP_FILE);
            JSON.writerWithDefaultPrettyPrinter().writeValue(manifestFile.toFile(), manifest);
        }
    }

    private void prepareWorker(Path directory, JsonNode background) throws IOException {
        var worker = ExtensionManifest.resolve(directory, background.path("service_worker").asText());
        var module = background.path("type").asText().equals("module");
        var statement = module ? "import '/" + BOOTSTRAP_FILE + "';" : "importScripts('/" + BOOTSTRAP_FILE + "');";
        Files.writeString(worker, ExtensionWorkerScript.prepend(Files.readString(worker), statement));
    }

    private void preparePage(Path directory, String relative) throws IOException {
        var page = ExtensionManifest.resolve(directory, relative);
        var source = Files.readString(page);
        var head = HEAD.matcher(source);
        if (!head.find()) {
            throw new IOException("The background page has no head element for the bootstrap");
        }

        var tag = "<script src=\"/" + BOOTSTRAP_FILE + "\"></script>";
        Files.writeString(page, source.substring(0, head.end()) + tag + source.substring(head.end()));
    }
}
