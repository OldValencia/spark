package io.loom.app.utils;

import com.google.gson.JsonParser;
import io.loom.app.ui.dialogs.UpdateDialog;
import javafx.application.Platform;
import javafx.stage.Window;
import lombok.extern.slf4j.Slf4j;

import java.awt.Desktop;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Slf4j
public class UpdateChecker {

    private static final String REPO_URL = "https://api.github.com/repos/oldvalencia/loom/releases/latest";

    public static void check(Window parentWindow) {
        new Thread(() -> {
            try (var client = HttpClient.newHttpClient()) {
                var request = HttpRequest.newBuilder()
                        .uri(URI.create(REPO_URL))
                        .build();

                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    var json = JsonParser.parseString(response.body()).getAsJsonObject();
                    var latestTag = json.get("tag_name").getAsString();

                    if (isNewerVersion(latestTag)) {
                        Platform.runLater(() -> showUpdateDialog(parentWindow, latestTag, json.get("html_url").getAsString()));
                    }
                }
            } catch (Exception e) {
                log.error("Error while checking new version", e);
            }
        }).start();
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
        var shouldUpdate = UpdateDialog.show(version, parent);

        if (shouldUpdate) {
            try {
                Desktop.getDesktop().browse(new URI(url));
            } catch (Exception ex) {
                log.error("Error while opening browser", ex);
            }
        }
    }
}
