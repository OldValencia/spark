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
            return providers == null ? new ArrayList<>() : refreshAccentColors(providers);
        } catch (IOException e) {
            log.error("Failed to load providers", e);
            return new ArrayList<>();
        }
    }

    /**
     * Re-derives the accent colour of providers that use a downloaded icon, so entries
     * created before the colour was extracted from the logo get corrected as well.
     * Bundled providers keep their curated colours: their icons are SVG, which the
     * extractor cannot read, and it leaves the stored value alone in that case.
     */
    private List<AiConfiguration.AiConfig> refreshAccentColors(List<AiConfiguration.AiConfig> providers) {
        var updated = new ArrayList<AiConfiguration.AiConfig>(providers.size());
        var changed = false;

        for (var provider : providers) {
            var color = accentFromIcon(provider.icon());
            if (color == null || color.equalsIgnoreCase(provider.color())) {
                updated.add(provider);
                continue;
            }

            log.info("Provider {}: accent colour {} -> {} (from its icon)", provider.name(), provider.color(), color);
            updated.add(new AiConfiguration.AiConfig(
                    provider.id(), provider.name(), provider.url(), color, provider.icon()));
            changed = true;
        }

        if (changed) {
            saveProviders(updated);
        }
        return updated;
    }

    /**
     * @return colour taken from the provider's downloaded icon, or {@code null} when
     *         there is no raster icon to read
     */
    private String accentFromIcon(String iconName) {
        if (iconName == null || iconName.isBlank() || iconName.toLowerCase().endsWith(".svg")) {
            return null;
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
        List<AiConfiguration.AiConfig> current = loadProviders();

        String id = "custom_" + UUID.randomUUID().toString().substring(0, 8);
        String iconFilename = downloadFavicon(url, id);

        current.add(new AiConfiguration.AiConfig(id, name, url, resolveAccentColor(url, iconFilename), iconFilename));
        saveProviders(current);
    }

    /**
     * Accent of a provider: the dominant colour of its logo, falling back to a hue
     * derived from the host so that it is at least stable across restarts.
     */
    private String resolveAccentColor(String url, String iconFilename) {
        var color = accentFromIcon(iconFilename);
        return color != null ? color : DominantColorExtractor.fromHost(url);
    }

    public synchronized void updateProvider(String id, String name, String url, String color) {
        List<AiConfiguration.AiConfig> current = loadProviders();
        for (int i = 0; i < current.size(); i++) {
            var existing = current.get(i);
            if (!existing.id().equals(id)) {
                continue;
            }

            var icon = existing.icon();
            var accent = color;

            // Moving a custom provider to another site invalidates its logo.
            if (id.startsWith("custom_") && !UrlUtils.isSameHost(url, existing.url())) {
                var newIcon = downloadFavicon(url, id);
                if (newIcon != null) {
                    icon = newIcon;
                }
                accent = resolveAccentColor(url, icon);
            }

            current.set(i, new AiConfiguration.AiConfig(id, name, url, accent, icon));
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

            try (InputStream in = faviconUri.toURL().openStream()) {
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
