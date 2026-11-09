package to.sparkapp.app.config;


import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import to.sparkapp.app.utils.DominantColorExtractor;
import to.sparkapp.app.utils.SvgImages;

@Slf4j
@RequiredArgsConstructor
final class ProviderIcons {
    private final File iconsDir;

    String accentFromIcon(String iconName) {
        if (iconName == null || iconName.isBlank()) {
            return null;
        }

        if (iconName.toLowerCase().endsWith(".svg")) {
            try {
                var image = SvgImages.render(new File(iconsDir, iconName).toURI().toURL(), 64, 0);

                return DominantColorExtractor.fromImage(image);
            } catch (IOException | RuntimeException error) {
                return null;
            }
        }

        return DominantColorExtractor.fromImage(new File(iconsDir, iconName));
    }

    String extractIconFromResources(String iconName) {
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

    String downloadFavicon(String urlString, String id) {
        try {
            var mainUri = URI.create(urlString);
            var host = mainUri.getHost();
            var faviconUri = URI.create("https://www.google.com/s2/favicons?domain=" + host + "&sz=64");
            var iconFile = new File(iconsDir, id + ".png");

            var connection = faviconUri.toURL().openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

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
