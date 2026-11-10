package to.sparkapp.app.config;

import java.io.File;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import to.sparkapp.app.config.AiConfiguration.AiConfig;
import to.sparkapp.app.utils.UrlUtils;

@Slf4j
public class CustomAiProvidersManager {

    private final ProviderIcons icons;
    private final ProvidersStore store;

    private final File configFile;

    @Getter
    private final File iconsDir;

    public CustomAiProvidersManager() {
        this.configFile = new File(AppPaths.DATA_DIR, "providers.json");
        this.iconsDir = new File(AppPaths.DATA_DIR, "icons");
        icons = new ProviderIcons(iconsDir);
        store = new ProvidersStore(configFile, icons);

        if (!AppPaths.DATA_DIR.exists() && !AppPaths.DATA_DIR.mkdirs()) {
            log.error("Failed to create config directory: {}", AppPaths.DATA_DIR);
        }

        if (!iconsDir.exists() && !iconsDir.mkdirs()) {
            log.error("Failed to create icons directory: {}", iconsDir);
        }
    }

    public synchronized List<AiConfig> loadProviders() {
        return store.loadProviders();
    }

    public synchronized void restoreDefaults() {
        store.restoreDefaults();
    }

    public synchronized void saveProviders(List<AiConfig> providers) {
        store.saveProviders(providers);
    }

    /** Adds a site and returns its id. */
    public synchronized String addCustomProvider(String name, String url, String manualColor) {
        List<AiConfig> current = loadProviders();

        String id = "custom_" + UUID.randomUUID().toString().substring(0, 8);
        String iconFilename = icons.downloadFavicon(url, id);

        var extracted = icons.accentFromIcon(iconFilename);
        var accent = manualColor != null ? manualColor : extracted != null ? extracted : randomAccent();
        current.add(new AiConfig(id, name, url, accent, iconFilename,
                manualColor != null ? "manual" : extracted != null ? "icon" : "random"));
        saveProviders(current);

        return id;
    }

    private static String randomAccent() {
        var palette = new String[]{"#D99B7D", "#A2BDDC", "#B7ABD7", "#D5B679", "#8EBFBA", "#D59BB9", "#BEC3D0"};

        return palette[ThreadLocalRandom.current().nextInt(palette.length)];
    }

    public synchronized void updateProvider(String id, String name, String url, String color, boolean useIconColor, boolean manualColor) {
        List<AiConfig> current = loadProviders();

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
                var newIcon = icons.downloadFavicon(url, id);
                icon = newIcon;
            }

            if (!useIconColor && color != null && (manualColor || !color.equalsIgnoreCase(existing.color()))) {
                accent = color;
                source = "manual";
            } else if (useIconColor || (hostChanged && !"manual".equals(source))) {
                var extracted = icons.accentFromIcon(icon);

                if (extracted != null) {
                    accent = extracted;
                    source = "icon";
                } else if (!"random".equals(source) || accent == null) {
                    accent = randomAccent();
                    source = "random";
                }
            }

            current.set(i, new AiConfig(id, name, url, accent, icon, source));
            break;
        }

        saveProviders(current);
    }

    public synchronized void deleteProvider(String id) {
        List<AiConfig> current = loadProviders();

        AiConfig toDelete = current.stream().filter(p -> p.id().equals(id)).findFirst().orElse(null);

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
}
