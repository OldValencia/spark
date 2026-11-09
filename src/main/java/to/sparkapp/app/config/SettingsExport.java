package to.sparkapp.app.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Packs the sites, their icons and the preferences into one ZIP file for another PC. There is no
 * import: the archive explains which folder to copy it into.
 */
public final class SettingsExport {
    private static final String CONFIG = "app-config.json";
    private static final String PROVIDERS = "providers.json";
    private static final String ICONS = "icons";
    /** Addresses of the pages the user had open: links to their own chats. */
    private static final List<String> PRIVATE_KEYS = List.of("siteUrls", "lastUrl", "lastSiteId");

    private final Path dataDirectory;
    private final ObjectMapper mapper = new ObjectMapper();

    public SettingsExport() {
        this(AppPaths.DATA_DIR.toPath());
    }

    SettingsExport(Path dataDirectory) {
        this.dataDirectory = dataDirectory;
    }

    /** A file name such as spark-settings-2026-11-08.zip. */
    public static String suggestedFileName() {
        return "spark-settings-" + LocalDate.now() + ".zip";
    }

    /** The folder an archive is copied into, written so it also fits another user's PC. */
    public static String restoreFolder() {
        return AppPaths.displayDir();
    }

    /** Writes the archive; a failed export leaves no partial file behind. */
    public void writeTo(Path target) throws IOException {
        try (var zip = new ZipOutputStream(Files.newOutputStream(target))) {
            addConfig(zip);
            addFile(zip, dataDirectory.resolve(PROVIDERS), PROVIDERS);
            addIcons(zip);
            addText(zip, "README.txt", readme());
        } catch (IOException | RuntimeException failure) {
            Files.deleteIfExists(target);
            throw failure;
        }
    }

    private void addConfig(ZipOutputStream zip) throws IOException {
        var file = dataDirectory.resolve(CONFIG);

        if (!Files.exists(file)) {
            return;
        }

        var config = (ObjectNode) mapper.readTree(file.toFile());
        config.remove(PRIVATE_KEYS);
        addText(zip, CONFIG, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(config));
    }

    private void addIcons(ZipOutputStream zip) throws IOException {
        var icons = dataDirectory.resolve(ICONS);

        if (!Files.isDirectory(icons)) {
            return;
        }

        try (var files = Files.list(icons)) {
            for (var icon : files.filter(Files::isRegularFile).sorted().toList()) {
                addFile(zip, icon, ICONS + "/" + icon.getFileName());
            }
        }
    }

    private static void addFile(ZipOutputStream zip, Path file, String name) throws IOException {
        if (!Files.exists(file)) {
            return;
        }

        zip.putNextEntry(new ZipEntry(name));
        Files.copy(file, zip);
        zip.closeEntry();
    }

    private static void addText(ZipOutputStream zip, String name, String text) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String readme() {
        return """
                Spark settings, exported %s

                To use them on this or another PC:
                1. Quit Spark: right-click the tray icon and choose Exit Spark.
                2. Copy everything from this archive into
                   %s
                   and replace the files that are already there.
                3. Start Spark.

                Included: your sites and their icons, size presets, window position, shortcut and
                other preferences.
                Not included: sign-ins and website data, extensions, logs, and the pages you had open.
                """.formatted(LocalDate.now(), restoreFolder()).replace("\n", "\r\n");
    }
}
