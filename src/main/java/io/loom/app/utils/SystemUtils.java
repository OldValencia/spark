package io.loom.app.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.Properties;

@Slf4j
public class SystemUtils {

    public static final String VERSION;

    static {
        var versionValue = "Dev-Build";
        try (var inputStream = SystemUtils.class.getResourceAsStream("/app.properties")) {
            if (inputStream != null) {
                var props = new Properties();
                props.load(inputStream);
                versionValue = props.getProperty("version", "Dev-Build");
            }
        } catch (Exception e) {
            log.error("Failed to load app version", e);
        }
        VERSION = versionValue;
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    public static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase().contains("mac");
    }
}
