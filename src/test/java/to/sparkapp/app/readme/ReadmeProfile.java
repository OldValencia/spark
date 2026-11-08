package to.sparkapp.app.readme;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import to.sparkapp.app.config.AiConfiguration;
import to.sparkapp.app.config.AppPaths;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.extensions.ExtensionImporter;

/**
 * Prepares the disposable capture profile: the default sites plus Wikipedia, and the sample
 * extensions downloaded by tools/extensions/download-samples.ps1. No user profile is read.
 */
final class ReadmeProfile {
    private static final Path SAMPLES = Path.of("local", "extensions-samples");
    private static final List<String> EXTENSIONS = List.of("darkreader.crx", "ublock.zip", "cookies.crx", "bitwarden.zip");

    private ReadmeProfile() {
    }

    static void prepare() throws Exception {
        new AiConfiguration(new AppPreferences());
        addWikipedia(new File(AppPaths.DATA_DIR, "providers.json").toPath());
        importExtensions(AppPaths.DATA_DIR.toPath().resolve("extensions"));
    }

    private static void addWikipedia(Path providers) throws Exception {
        var json = new ObjectMapper();
        List<Map<String, Object>> sites = new ArrayList<>(json.readValue(providers.toFile(), new TypeReference<>() {}));
        if (sites.stream().noneMatch(site -> "wikipedia".equals(site.get("id")))) {
            var icon = downloadIcon(providers.resolveSibling("icons").resolve("wikipedia.png"));
            sites.add(1, Map.of("id", "wikipedia", "name", "Wikipedia", "url", "https://en.wikipedia.org/wiki/Main_Page",
                    "color", "#d9d9de", "icon", icon));
            json.writerWithDefaultPrettyPrinter().writeValue(providers.toFile(), sites);
        }
    }

    /** Uses the same favicon service as adding a site in Spark. */
    private static String downloadIcon(Path target) throws Exception {
        Files.createDirectories(target.getParent());
        var uri = URI.create("https://www.google.com/s2/favicons?domain=en.wikipedia.org&sz=64");
        try (var input = uri.toURL().openStream()) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }

        return target.getFileName().toString();
    }

    private static void importExtensions(Path folder) throws Exception {
        Files.createDirectories(folder);
        try (var existing = Files.list(folder)) {
            if (existing.anyMatch(Files::isDirectory)) {
                return;
            }
        }
        for (var name : EXTENSIONS) {
            var archive = SAMPLES.resolve(name);
            if (!Files.isRegularFile(archive)) {
                throw new IllegalStateException("Run tools/extensions/download-samples.ps1 first; missing " + archive);
            }
            new ExtensionImporter().importArchive(archive, folder);
        }
    }
}
