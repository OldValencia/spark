package io.loom.app.utils;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileWriter;

@Slf4j
public class AutoStartManager {
    private static final String APP_NAME = "Loom";

    public static void setAutoStart(boolean enable) {
        var os = System.getProperty("os.name").toLowerCase();

        try {
            var executablePath = ProcessHandle.current().info().command().orElse(null);

            if (executablePath == null) {
                log.warn("Can't get executable file path, skipping");
                return;
            }

            if (os.contains("win")) {
                handleWindows(enable, executablePath);
            } else if (os.contains("mac")) {
                handleMac(enable, executablePath);
            }
        } catch (Exception e) {
            log.error("Can't set auto start for OS: {}", os, e);
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
        var userHome = System.getProperty("user.home");
        var launchAgentsDir = new File(userHome, "Library/LaunchAgents");
        if (!launchAgentsDir.exists()) {
            launchAgentsDir.mkdirs();
        }

        var plistFile = new File(launchAgentsDir, "io.loom.app.plist");

        if (enable) {
            String plistContent =
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                            "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n" +
                            "<plist version=\"1.0\">\n" +
                            "<dict>\n" +
                            "    <key>Label</key>\n" +
                            "    <string>io.loom.app</string>\n" +
                            "    <key>ProgramArguments</key>\n" +
                            "    <array>\n" +
                            "        <string>" + path + "</string>\n" +
                            "    </array>\n" +
                            "    <key>RunAtLoad</key>\n" +
                            "    <true/>\n" +
                            "</dict>\n" +
                            "</plist>";

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