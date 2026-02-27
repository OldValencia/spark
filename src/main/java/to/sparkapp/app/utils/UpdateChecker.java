package to.sparkapp.app.utils;

import com.google.gson.JsonParser;
import to.sparkapp.app.ui.dialogs.UpdateDialog;
import javafx.application.Platform;
import javafx.stage.Window;
import lombok.extern.slf4j.Slf4j;

import java.awt.Desktop;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class UpdateChecker {

    private static final String REPO_URL = "https://api.github.com/repos/oldvalencia/spark/releases/latest";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    public static void check(Window parentWindow) {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(REPO_URL))
                .timeout(Duration.ofSeconds(10))
                .build();

        CompletableFuture
                .supplyAsync(() -> {
                    try {
                        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .thenAccept(response -> {
                    if (response.statusCode() != 200) return;
                    try {
                        var json = JsonParser.parseString(response.body()).getAsJsonObject();
                        var latestTag = json.get("tag_name").getAsString();
                        var htmlUrl = json.get("html_url").getAsString();
                        if (isNewerVersion(latestTag)) {
                            Platform.runLater(() -> showUpdateDialog(parentWindow, latestTag, htmlUrl));
                        }
                    } catch (Exception e) {
                        log.warn("Failed to parse GitHub release response", e);
                    }
                })
                .exceptionally(e -> {
                    log.debug("Update check failed: {}", e.getMessage());
                    return null;
                });
    }

    private static boolean isNewerVersion(String latest) {
        try {
            var l = latest.replace("v", "").replace("Dev-Build", "0.0.0").trim();
            var c = SystemUtils.VERSION.replace("v", "").replace("Dev-Build", "0.0.0").trim();
            var lParts = l.split("\\.");
            var cParts = c.split("\\.");
            int length = Math.max(lParts.length, cParts.length);
            for (int i = 0; i < length; i++) {
                int lVal = i < lParts.length ? Integer.parseInt(lParts[i]) : 0;
                int cVal = i < cParts.length ? Integer.parseInt(cParts[i]) : 0;
                if (lVal > cVal) return true;
                if (lVal < cVal) return false;
            }
            return false;
        } catch (Exception e) {
            return !latest.equals(SystemUtils.VERSION);
        }
    }

    private static void showUpdateDialog(Window parent, String version, String url) {
        if (!UpdateDialog.show(version, parent)) return;
        try {
            Desktop.getDesktop().browse(new URI(url));
        } catch (Exception e) {
            log.error("Error opening browser for update", e);
        }
    }
}
