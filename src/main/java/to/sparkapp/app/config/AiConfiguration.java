package to.sparkapp.app.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class AiConfiguration {

    @Getter
    private final CustomAiProvidersManager customProvidersManager;

    @Getter
    private List<AiConfig> configurations = new ArrayList<>();

    private final AppPreferences appPreferences;

    public AiConfiguration(AppPreferences appPreferences) {
        this.appPreferences = appPreferences;
        this.customProvidersManager = new CustomAiProvidersManager();
        reload();
    }

    public void reload() {
        this.configurations = customProvidersManager.loadProviders();
        log.info("AiConfiguration loaded {} providers", configurations.size());

        if (appPreferences != null) {
            List<String> validUrls = configurations.stream()
                    .map(AiConfig::url)
                    .collect(Collectors.toList());
            appPreferences.cleanupLastUrlIfNeeded(validUrls);
        }
    }

    public void resetToDefaults() {
        customProvidersManager.restoreDefaults();
        reload();
    }

    public record AiConfig(
            String id,
            String name,
            String url,
            String color,
            String icon
    ) {}
}
