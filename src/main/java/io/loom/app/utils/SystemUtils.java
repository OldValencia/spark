package io.loom.app.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.Properties;

@Slf4j
public class SystemUtils {

    public static final String VERSION;

    static {
        String v = null;
        try (var is = SystemUtils.class.getResourceAsStream("/app.properties")) {
            if (is != null) {
                var props = new Properties();
                props.load(is);
                v = props.getProperty("version", "Dev-Build");
            }
        } catch (Exception e) {
            log.error("Failed to load app version", e);
        }
        VERSION = v;
    }


    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
