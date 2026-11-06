package to.sparkapp.app.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import to.sparkapp.app.config.AiConfiguration.AiConfig;

@Slf4j
@RequiredArgsConstructor
final class ProvidersStore {
    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final File configFile;
    private final ProviderIcons icons;

    public synchronized List<AiConfig> loadProviders() {
        if ((!configFile.exists() || configFile.length() == 0) && !new File(configFile + ".bak").exists()) {
            log.info("Configuration file not found. Loading defaults from resources...");
            restoreDefaults();
        }

        try {
            List<AiConfig> providers = JsonFiles.read(configFile,
                    jsonMapper.readerFor(new TypeReference<List<AiConfig>>() {
                    }));

            return providers == null ? new ArrayList<>() : providers;
        } catch (IOException e) {
            log.error("Failed to load providers", e);

            return new ArrayList<>();
        }
    }

    public synchronized void restoreDefaults() {
        try {
            InputStream inputStream = getClass().getResourceAsStream("/default-providers.json");

            if (inputStream == null) {
                log.error("Default providers file not found!");

                return;
            }

            var defaultConfigs = jsonMapper.readValue(inputStream, new TypeReference<List<AiConfig>>() {
            });

            List<AiConfig> processedConfigs = new ArrayList<>();

            for (AiConfig config : defaultConfigs) {
                String localIconName = icons.extractIconFromResources(config.icon());

                processedConfigs.add(new AiConfig(config.id(), config.name(), config.url(), config.color(),
                        localIconName != null ? localIconName : config.icon()));
            }

            saveProviders(processedConfigs);
            log.info("Restored default providers and icons.");

        } catch (Exception e) {
            log.error("Failed to restore defaults", e);
        }
    }

    public synchronized void saveProviders(List<AiConfig> providers) {
        try {
            JsonFiles.write(configFile, jsonMapper, providers);
            log.info("Saved {} providers to {}", providers.size(), configFile.getAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to save providers", e);
        }
    }
}
