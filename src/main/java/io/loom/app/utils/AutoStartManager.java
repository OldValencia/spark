package io.loom.app.utils;

import io.loom.app.config.AppPreferences;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileWriter;

@Slf4j
public class AutoStartManager {
    private static final String APP_NAME = "Loom";

    public static void setAutoStart(boolean enable) {
        try {
            var executablePath = ProcessHandle.current().info().command().orElse(null);

            if (executablePath == null) {
                log.warn("Can't get executable file path, skipping");
                return;
            }

            if (SystemUtils.isWindows()) {
                handleWindows(enable, executablePath);
            } else if (SystemUtils.isMac()) {
                handleMac(enable, executablePath);
            }
        } catch (Exception e) {
            log.error("Can't set auto start", e);
        }
    }

    private static void handleWindows(boolean enable, String path) throws Exception {
        var key = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
        if (enable) {
            new ProcessBuilder("reg", "add", key, "/v", APP_NAME, "/t", "REG_SZ", "/d", path, "/f").start();
        } else {
            new ProcessBuilder("reg", "delete", key, "/v", APP_NAME, "/f").start();
        }
    }

    private static void handleMac(boolean enable, String path) throws Exception {
        var launchAgentsDir = new File(AppPreferences.DATA_DIR, "Library/LaunchAgents");
        if (!launchAgentsDir.exists()) {
            launchAgentsDir.mkdirs();
        }

        var plistFile = new File(launchAgentsDir, "io.loom.app.plist");

        if (enable) {
            var plistContent = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                    <plist version="1.0">
                    <dict>
                        <key>Label</key>
                        <string>io.loom.app</string>
                        <key>ProgramArguments</key>
                        <array>
                            <string>%s</string>
                        </array>
                        <key>RunAtLoad</key>
                        <true/>
                    </dict>
                    </plist>
                    """.formatted(path);

            try (var writer = new FileWriter(plistFile)) {
                writer.write(plistContent);
            }
        } else {
            if (plistFile.exists()) {
                plistFile.delete();
            }
        }
    }
}
