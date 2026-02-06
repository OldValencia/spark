package io.loom.app.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class CustomAiProvidersManager {

    private static final String USER_DIR = System.getProperty("user.home") + File.separator + ".loom";
    private static final String PROVIDERS_FILE = USER_DIR + File.separator + "custom-providers.json";
    private static final String ICONS_DIR = USER_DIR + File.separator + "icons";

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private List<CustomProvider> customProviders = new ArrayList<>();

    @Data
    public static class CustomProvider {
        private String id;
        private String name;
        private String url;
        private String icon;
        private String color;
        private boolean isCustom = true;

        public CustomProvider() {
        }

        public CustomProvider(String name, String url) {
            this.id = generateId(name);
            this.name = name;
            this.url = url;
        }

        private static String generateId(String name) {
            return "custom_" + name.toLowerCase().replaceAll("[^a-z0-9]", "_");
        }

        public AiConfiguration.AiConfig toAiConfig() {
            return new AiConfiguration.AiConfig(name, url, icon, color);
        }
    }

    public CustomAiProvidersManager() {
        ensureDirectories();
        load();
    }

    private void ensureDirectories() {
        try {
            Files.createDirectories(Paths.get(USER_DIR));
            Files.createDirectories(Paths.get(ICONS_DIR));
        } catch (IOException e) {
            log.error("Failed to create directories", e);
        }
    }

    public void load() {
        var file = new File(PROVIDERS_FILE);
        if (!file.exists()) {
            customProviders = new ArrayList<>();
            return;
        }

        try (var reader = new FileReader(file)) {
            var type = new TypeToken<List<CustomProvider>>(){}.getType();
            customProviders = gson.fromJson(reader, type);
            if (customProviders == null) {
                customProviders = new ArrayList<>();
            }
            log.info("Loaded {} custom AI providers", customProviders.size());
        } catch (Exception e) {
            log.error("Failed to load custom providers", e);
            customProviders = new ArrayList<>();
        }
    }

    public void save() {
        try (var writer = new FileWriter(PROVIDERS_FILE)) {
            gson.toJson(customProviders, writer);
            log.info("Saved {} custom AI providers", customProviders.size());
        } catch (IOException e) {
            log.error("Failed to save custom providers", e);
        }
    }

    public CustomProvider addProvider(String name, String url) {
        var provider = new CustomProvider(name, url);

        try {
            var iconPath = downloadAndSaveFavicon(url, provider.getId());
            provider.setIcon(iconPath);

            var color = extractDominantColor(iconPath);
            provider.setColor(color);

        } catch (Exception e) {
            log.error("Failed to process icon for provider: {}", name, e);
            provider.setIcon("default.png");
            provider.setColor("#808080");
        }

        customProviders.add(provider);
        save();

        log.info("Added custom provider: {} ({})", name, url);
        return provider;
    }

    public void updateProvider(String id, String newName, String newUrl) {
        var provider = findById(id);
        if (provider == null) {
            log.warn("Provider not found: {}", id);
            return;
        }

        boolean urlChanged = !provider.getUrl().equals(newUrl);

        provider.setName(newName);
        provider.setUrl(newUrl);

        if (urlChanged) {
            try {
                var iconPath = downloadAndSaveFavicon(newUrl, provider.getId());
                provider.setIcon(iconPath);

                var color = extractDominantColor(iconPath);
                provider.setColor(color);
            } catch (Exception e) {
                log.error("Failed to update icon for provider: {}", newName, e);
            }
        }

        save();
        log.info("Updated custom provider: {}", id);
    }

    public void deleteProvider(String id) {
        var provider = findById(id);
        if (provider == null) {
            log.warn("Provider not found: {}", id);
            return;
        }

        // Удаляем иконку
        if (provider.getIcon() != null) {
            var iconFile = new File(ICONS_DIR, provider.getIcon());
            if (iconFile.exists()) {
                iconFile.delete();
            }
        }

        customProviders.remove(provider);
        save();

        log.info("Deleted custom provider: {}", id);
    }

    public List<CustomProvider> getAllProviders() {
        return new ArrayList<>(customProviders);
    }

    public CustomProvider findById(String id) {
        return customProviders.stream()
                .filter(p -> p.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    private String downloadAndSaveFavicon(String url, String providerId) throws IOException {
        String[] faviconPaths = {
                "/favicon.ico",
                "/favicon.png",
                "/apple-touch-icon.png",
                "/apple-touch-icon-precomposed.png"
        };

        var baseUrl = extractBaseUrl(url);
        BufferedImage favicon = null;

        for (var path : faviconPaths) {
            try {
                var faviconUrl = new URL(baseUrl + path);
                favicon = ImageIO.read(faviconUrl);
                if (favicon != null) {
                    log.info("Downloaded favicon from: {}", faviconUrl);
                    break;
                }
            } catch (Exception e) {
                // Пробуем следующий путь
            }
        }

        if (favicon == null) {
            try {
                var googleFaviconUrl = new URL("https://www.google.com/s2/favicons?domain=" + baseUrl + "&sz=64");
                favicon = ImageIO.read(googleFaviconUrl);
                log.info("Downloaded favicon from Google service");
            } catch (Exception e) {
                log.warn("Failed to download favicon from Google service", e);
            }
        }

        if (favicon == null) {
            favicon = createDefaultIcon(providerId);
        }

        var resized = resizeIcon(favicon, 64, 64);
        var filename = providerId + ".png";
        var iconFile = new File(ICONS_DIR, filename);
        ImageIO.write(resized, "PNG", iconFile);

        return filename;
    }

    private String extractBaseUrl(String url) {
        try {
            var urlObj = new URL(url);
            return urlObj.getProtocol() + "://" + urlObj.getHost();
        } catch (Exception e) {
            return url;
        }
    }

    private BufferedImage createDefaultIcon(String text) {
        var img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        var g = img.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g.setColor(new Color(100, 160, 255));
        g.fillRoundRect(0, 0, 64, 64, 16, 16);

        var firstLetter = text.isEmpty() ? "?" : text.substring(0, 1).toUpperCase();
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 32));

        var fm = g.getFontMetrics();
        var x = (64 - fm.stringWidth(firstLetter)) / 2;
        var y = (64 + fm.getAscent() - fm.getDescent()) / 2;
        g.drawString(firstLetter, x, y);

        g.dispose();
        return img;
    }

    private BufferedImage resizeIcon(BufferedImage original, int width, int height) {
        var resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(original, 0, 0, width, height, null);
        g.dispose();
        return resized;
    }

    private String extractDominantColor(String iconPath) {
        try {
            var iconFile = new File(ICONS_DIR, iconPath);
            var img = ImageIO.read(iconFile);
            long sumR = 0, sumG = 0, sumB = 0;
            int count = 0;

            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    var rgb = img.getRGB(x, y);
                    var alpha = (rgb >> 24) & 0xFF;

                    if (alpha < 50) continue;

                    sumR += (rgb >> 16) & 0xFF;
                    sumG += (rgb >> 8) & 0xFF;
                    sumB += rgb & 0xFF;
                    count++;
                }
            }

            if (count == 0) {
                return "#808080";
            }

            var avgR = (int) (sumR / count);
            var avgG = (int) (sumG / count);
            var avgB = (int) (sumB / count);
            var color = enhanceSaturation(avgR, avgG, avgB);

            return String.format("#%02X%02X%02X", color[0], color[1], color[2]);
        } catch (Exception e) {
            log.error("Failed to extract dominant color", e);
            return "#808080";
        }
    }

    private int[] enhanceSaturation(int r, int g, int b) {
        float[] hsv = new float[3];
        Color.RGBtoHSB(r, g, b, hsv);
        hsv[1] = Math.min(1.0f, hsv[1] * 1.3f);

        var rgb = Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]);
        return new int[]{
                (rgb >> 16) & 0xFF,
                (rgb >> 8) & 0xFF,
                rgb & 0xFF
        };
    }
}