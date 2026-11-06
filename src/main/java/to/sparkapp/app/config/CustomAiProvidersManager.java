package to.sparkapp.app.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import to.sparkapp.app.utils.DominantColorExtractor;
import to.sparkapp.app.utils.UrlUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.awt.image.BufferedImage;

@Slf4j
public class CustomAiProvidersManager {

    private final ObjectMapper jsonMapper = new ObjectMapper();

    private final File configFile;

    @Getter
    private final File iconsDir;

    public CustomAiProvidersManager() {
        this.configFile = new File(AppPaths.DATA_DIR, "providers.json");
        this.iconsDir = new File(AppPaths.DATA_DIR, "icons");

        if (!AppPaths.DATA_DIR.exists() && !AppPaths.DATA_DIR.mkdirs()) {
            log.error("Failed to create config directory: {}", AppPaths.DATA_DIR);
        }
        if (!iconsDir.exists() && !iconsDir.mkdirs()) {
            log.error("Failed to create icons directory: {}", iconsDir);
        }
    }

    public synchronized List<AiConfiguration.AiConfig> loadProviders() {
        if ((!configFile.exists() || configFile.length() == 0) && !new File(configFile + ".bak").exists()) {
            log.info("Configuration file not found. Loading defaults from resources...");
            restoreDefaults();
        }

        try {
            List<AiConfiguration.AiConfig> providers = JsonFiles.read(configFile, jsonMapper.readerFor(new TypeReference<List<AiConfiguration.AiConfig>>() {}));
            return providers == null ? new ArrayList<>() : providers;
        } catch (IOException e) {
            log.error("Failed to load providers", e);
            return new ArrayList<>();
        }
    }

    /**
     * @return colour taken from the provider's downloaded icon, or {@code null} when
     *         there is no raster icon to read
     */
    private String accentFromIcon(String iconName) {
        if (iconName == null || iconName.isBlank()) {
            return null;
        }
        if (iconName.toLowerCase().endsWith(".svg")) {
            try {
                var icon = new FlatSVGIcon(new File(iconsDir, iconName)).derive(64, 64);
                var image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
                var graphics = image.createGraphics();
                try { icon.paintIcon(null, graphics, 0, 0); } finally { graphics.dispose(); }
                return DominantColorExtractor.fromImage(image);
            } catch (RuntimeException error) { return null; }
        }
        return DominantColorExtractor.fromImage(new File(iconsDir, iconName));
    }

    public synchronized void restoreDefaults() {
        try {
            InputStream inputStream = getClass().getResourceAsStream("/default-providers.json");

            if (inputStream == null) {
                log.error("Default providers file not found!");
                return;
            }

            var defaultConfigs = jsonMapper.readValue(
                    inputStream,
                    new TypeReference<List<AiConfiguration.AiConfig>>() {}
            );

            List<AiConfiguration.AiConfig> processedConfigs = new ArrayList<>();

            for (AiConfiguration.AiConfig config : defaultConfigs) {
                String localIconName = extractIconFromResources(config.icon());

                processedConfigs.add(new AiConfiguration.AiConfig(
                        config.id(),
                        config.name(),
                        config.url(),
                        config.color(),
                        localIconName != null ? localIconName : config.icon()
                ));
            }

            saveProviders(processedConfigs);
            log.info("Restored default providers and icons.");

        } catch (Exception e) {
            log.error("Failed to restore defaults", e);
        }
    }

    private String extractIconFromResources(String iconName) {
        if (iconName == null || iconName.isEmpty()) {
            return null;
        }

        try {
            String resourcePath = "/icons/" + iconName;
            InputStream in = getClass().getResourceAsStream(resourcePath);

            if (in == null) {
                log.warn("Icon resource not found: {}", resourcePath);
                return null;
            }

            File targetFile = new File(iconsDir, iconName);
            Files.copy(in, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            in.close();

            log.info("Extracted icon: {} -> {}", resourcePath, targetFile.getAbsolutePath());
            return iconName;

        } catch (IOException e) {
            log.error("Failed to extract icon: " + iconName, e);
            return null;
        }
    }

    public synchronized void saveProviders(List<AiConfiguration.AiConfig> providers) {
        try {
            JsonFiles.write(configFile, jsonMapper, providers);
            log.info("Saved {} providers to {}", providers.size(), configFile.getAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to save providers", e);
        }
    }

    public synchronized void addCustomProvider(String name, String url) {
        addCustomProvider(name, url, null);
    }

    public synchronized void addCustomProvider(String name, String url, String manualColor) {
        List<AiConfiguration.AiConfig> current = loadProviders();

        String id = "custom_" + UUID.randomUUID().toString().substring(0, 8);
        String iconFilename = downloadFavicon(url, id);

        var extracted = accentFromIcon(iconFilename);
        var accent = manualColor != null ? manualColor : extracted != null ? extracted : randomAccent();
        current.add(new AiConfiguration.AiConfig(id, name, url, accent, iconFilename,
                manualColor != null ? "manual" : extracted != null ? "icon" : "random"));
        saveProviders(current);
    }

    private static String randomAccent() {
        var palette = new String[]{"#D99B7D", "#A2BDDC", "#B7ABD7", "#D5B679", "#8EBFBA", "#D59BB9", "#BEC3D0"};
        return palette[ThreadLocalRandom.current().nextInt(palette.length)];
    }

    public synchronized void updateProvider(String id, String name, String url, String color) {
        updateProvider(id, name, url, color, false);
    }

    public synchronized void updateProvider(String id, String name, String url, String color, boolean useIconColor) {
        updateProvider(id, name, url, color, useIconColor, false);
    }

    public synchronized void updateProvider(String id, String name, String url, String color, boolean useIconColor, boolean manualColor) {
        List<AiConfiguration.AiConfig> current = loadProviders();
        for (int i = 0; i < current.size(); i++) {
            var existing = current.get(i);
            if (!existing.id().equals(id)) {
                continue;
            }

            var icon = existing.icon();
            var accent = existing.color();
            var source = existing.colorSource();
            boolean hostChanged = !UrlUtils.isSameHost(url, existing.url());

            // Moving a custom provider to another site invalidates its logo.
            if (hostChanged) {
                var newIcon = downloadFavicon(url, id);
                icon = newIcon;
            }
            if (!useIconColor && color != null && (manualColor || !color.equalsIgnoreCase(existing.color()))) {
                accent = color; source = "manual";
            } else if (useIconColor || (hostChanged && !"manual".equals(source))) {
                var extracted = accentFromIcon(icon);
                if (extracted != null) { accent = extracted; source = "icon"; }
                else if (!"random".equals(source) || accent == null) { accent = randomAccent(); source = "random"; }
            }
            current.set(i, new AiConfiguration.AiConfig(id, name, url, accent, icon, source));
            break;
        }
        saveProviders(current);
    }

    public synchronized void deleteProvider(String id) {
        List<AiConfiguration.AiConfig> current = loadProviders();

        AiConfiguration.AiConfig toDelete = current.stream()
                .filter(p -> p.id().equals(id))
                .findFirst()
                .orElse(null);

        current.removeIf(p -> p.id().equals(id));
        saveProviders(current);

        if (toDelete != null && toDelete.icon() != null && id.startsWith("custom_")) {
            File iconFile = new File(iconsDir, toDelete.icon());
            if (iconFile.exists()) {
                iconFile.delete();
                log.info("Deleted icon: {}", iconFile.getAbsolutePath());
            }
        }
    }

    private String downloadFavicon(String urlString, String id) {
        try {
            var mainUri = URI.create(urlString);
            var host = mainUri.getHost();
            var faviconUri = URI.create("https://www.google.com/s2/favicons?domain=" + host + "&sz=64");
            var iconFile = new File(iconsDir, id + ".png");

            var connection = faviconUri.toURL().openConnection();
            connection.setConnectTimeout(5000); connection.setReadTimeout(5000);
            try (InputStream in = connection.getInputStream()) {
                Files.copy(in, iconFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            if (iconFile.length() < 100) {
                log.warn("Downloaded icon is too small (likely placeholder): {}", iconFile.length());
                Files.deleteIfExists(iconFile.toPath());
                return null;
            }

            log.info("Downloaded favicon: {}", iconFile.getAbsolutePath());
            return iconFile.getName();
        } catch (Exception e) {
            log.error("Failed to download favicon for " + urlString, e);
            return null;
        }
    }
}
