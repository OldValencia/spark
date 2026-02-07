package io.loom.app.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

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
        this.configFile = new File(AppPreferences.DATA_DIR, "providers.json");
        this.iconsDir = new File(AppPreferences.DATA_DIR, "icons");

        if (!AppPreferences.DATA_DIR.exists() && !AppPreferences.DATA_DIR.mkdirs()) {
            log.error("Failed to create config directory: {}", AppPreferences.DATA_DIR);
        }
        if (!iconsDir.exists() && !iconsDir.mkdirs()) {
            log.error("Failed to create icons directory: {}", iconsDir);
        }
    }

    public List<AiConfiguration.AiConfig> loadProviders() {
        if (!configFile.exists() || configFile.length() == 0) {
            log.info("Configuration file not found. Loading defaults from resources...");
            restoreDefaults();
        }

        try {
            return jsonMapper.readValue(configFile, new TypeReference<>() {});
        } catch (IOException e) {
            log.error("Failed to load providers", e);
            return new ArrayList<>();
        }
    }

    public void restoreDefaults() {
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

    public void saveProviders(List<AiConfiguration.AiConfig> providers) {
        try {
            jsonMapper.writerWithDefaultPrettyPrinter().writeValue(configFile, providers);
            log.info("Saved {} providers to {}", providers.size(), configFile.getAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to save providers", e);
        }
    }

    public void addCustomProvider(String name, String url, String color) {
        List<AiConfiguration.AiConfig> current = loadProviders();

        String id = "custom_" + UUID.randomUUID().toString().substring(0, 8);
        String iconFilename = downloadFavicon(url, id);

        current.add(new AiConfiguration.AiConfig(id, name, url, color, iconFilename));
        saveProviders(current);
    }

    public void updateProvider(String id, String name, String url, String color) {
        List<AiConfiguration.AiConfig> current = loadProviders();
        for (int i = 0; i < current.size(); i++) {
            if (current.get(i).id().equals(id)) {
                String oldIcon = current.get(i).icon();
                current.set(i, new AiConfiguration.AiConfig(id, name, url, color, oldIcon));
                break;
            }
        }
        saveProviders(current);
    }

    public void deleteProvider(String id) {
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
