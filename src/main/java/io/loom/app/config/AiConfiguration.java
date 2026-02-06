package io.loom.app.config;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Getter
@Setter
public class AiConfiguration {

    private List<AiConfig> configurations;
    private CustomAiProvidersManager customProvidersManager;

    public AiConfiguration() {
        var builtInProviders = loadBuiltInProviders();

        customProvidersManager = new CustomAiProvidersManager();
        var customProviders = customProvidersManager.getAllProviders().stream()
                .map(CustomAiProvidersManager.CustomProvider::toAiConfig)
                .toList();

        configurations = new ArrayList<>();
        configurations.addAll(builtInProviders);
        configurations.addAll(customProviders);

        log.info("Loaded {} built-in and {} custom AI providers",
                builtInProviders.size(), customProviders.size());
    }

    private List<AiConfig> loadBuiltInProviders() {
        try {
            var yaml = new Yaml();
            var inputStream = this.getClass()
                    .getClassLoader()
                    .getResourceAsStream("ai-configurations.yml");

            if (inputStream == null) {
                log.warn("ai-configurations.yml not found, using empty list");
                return new ArrayList<>();
            }

            Map<String, List<Map<String, String>>> obj = yaml.load(inputStream);

            return obj.get("configurations").stream()
                    .map(m -> new AiConfig(
                            m.get("name"),
                            m.get("url"),
                            m.get("icon"),
                            m.get("color"),
                            false  // built-in
                    ))
                    .toList();
        } catch (Exception e) {
            log.error("Failed to load built-in providers", e);
            return new ArrayList<>();
        }
    }

    public void reload() {
        var builtInProviders = loadBuiltInProviders();

        customProvidersManager.load();
        var customProviders = customProvidersManager.getAllProviders().stream()
                .map(CustomAiProvidersManager.CustomProvider::toAiConfig)
                .toList();

        configurations = new ArrayList<>();
        configurations.addAll(builtInProviders);
        configurations.addAll(customProviders);

        log.info("Reloaded {} built-in and {} custom AI providers",
                builtInProviders.size(), customProviders.size());
    }

    public record AiConfig(
            String name,
            String url,
            String icon,
            String color,
            boolean isCustom
    ) {
        public AiConfig(String name, String url, String icon, String color) {
            this(name, url, icon, color, false);
        }

        public String getIconPath() {
            if (isCustom) {
                var userDir = System.getProperty("user.home") + File.separator + ".loom";
                return userDir + File.separator + "icons" + File.separator + icon;
            } else {
                return "/icons/" + icon;
            }
        }
    }
}