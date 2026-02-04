package io.loom.app.config;

import io.loom.app.utils.AutoStartManager;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.apache.commons.collections4.ListUtils.emptyIfNull;

@Slf4j
public class AppPreferences {

    private static final String FILE_NAME = "settings.properties";
    private static final String DIR = System.getProperty("user.home") + File.separator + ".loom";
    private static final File FILE = new File(DIR, FILE_NAME);

    private final Properties props = new Properties();

    private static final String KEY_LAST_URL = "last_url";
    private static final String KEY_REMEMBER_AI = "remember_last_ai";
    private static final String KEY_LAST_ZOOM_VALUE = "last_zoom_value";
    private static final String KEY_ZOOM_ENABLED = "zoom_enabled";
    private static final String KEY_AI_ORDER = "ai_order";
    private static final String KEY_CHECK_UPDATES_ON_STARTUP = "check_updates_on_startup";
    private static final String KEY_AUTO_START_ENABLED = "auto_start_enabled";
    private static final String KEY_HOTKEY_TO_START_APPLICATION = "hotkey_to_start_application";

    public AppPreferences() {
        load();
        initDefaults();
    }

    private void load() {
        if (!FILE.exists()) return;
        try (var is = new FileInputStream(FILE)) {
            props.load(is);
        } catch (IOException e) {
            log.error("Failed to load application preferences", e);
        }
    }

    private void initDefaults() {
        boolean changed = false;

        if (!props.containsKey(KEY_REMEMBER_AI)) {
            props.setProperty(KEY_REMEMBER_AI, "true");
            changed = true;
        }
        if (!props.containsKey(KEY_ZOOM_ENABLED)) {
            props.setProperty(KEY_ZOOM_ENABLED, "true");
            changed = true;
        }
        if (!props.containsKey(KEY_LAST_ZOOM_VALUE)) {
            props.setProperty(KEY_LAST_ZOOM_VALUE, "0.0");
            changed = true;
        }
        if (!props.containsKey(KEY_CHECK_UPDATES_ON_STARTUP)) {
            props.setProperty(KEY_CHECK_UPDATES_ON_STARTUP, "true");
            changed = true;
        }
        if (!props.containsKey(KEY_AUTO_START_ENABLED)) {
            props.setProperty(KEY_AUTO_START_ENABLED, "true");
            changed = true;
        }
        if (!props.containsKey(KEY_AI_ORDER)) {
            props.setProperty(KEY_AI_ORDER, "");
            changed = true;
        }
        if (!props.containsKey(KEY_HOTKEY_TO_START_APPLICATION)) {
            props.setProperty(KEY_HOTKEY_TO_START_APPLICATION, "");
            changed = true;
        }

        if (changed) {
            save();
        }
    }

    private void save() {
        new File(DIR).mkdirs();
        try (var os = new FileOutputStream(FILE)) {
            props.store(os, "Loom Settings");
        } catch (IOException e) {
            log.error("Failed to save application preferences", e);
        }
    }

    public void setLastUrl(String url) {
        if (isRememberLastAi()) {
            props.setProperty(KEY_LAST_URL, url);
            save();
        }
    }

    public String getLastUrl() {
        return props.getProperty(KEY_LAST_URL);
    }

    public void setRememberLastAi(boolean remember) {
        props.setProperty(KEY_REMEMBER_AI, String.valueOf(remember));
        save();
        if (!remember) {
            props.remove(KEY_LAST_URL);
            save();
        }
    }

    public boolean isRememberLastAi() {
        return Boolean.parseBoolean(props.getProperty(KEY_REMEMBER_AI, "true"));
    }

    public void setLastZoomValue(Double zoomValue) {
        if (isZoomEnabled()) {
            props.setProperty(KEY_LAST_ZOOM_VALUE, String.valueOf(zoomValue));
            save();
        }
    }

    public Double getLastZoomValue() {
        try {
            return Double.valueOf(props.getProperty(KEY_LAST_ZOOM_VALUE, "0.0"));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    public void setZoomEnabled(boolean zoomEnabled) {
        props.setProperty(KEY_ZOOM_ENABLED, String.valueOf(zoomEnabled));
        save();
        if (!zoomEnabled) {
            setLastZoomValue(0.0);
        }
    }

    public boolean isZoomEnabled() {
        return Boolean.parseBoolean(props.getProperty(KEY_ZOOM_ENABLED, "true"));
    }

    public void setAiOrder(List<String> urls) {
        var order = String.join(",", urls);
        props.setProperty(KEY_AI_ORDER, order);
        save();
    }

    public List<String> getAiOrder() {
        var orderStr = props.getProperty(KEY_AI_ORDER);
        if (orderStr == null || orderStr.isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.stream(orderStr.split(","))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    public void setCheckUpdatesOnStartup(boolean checkUpdatesOnStartup) {
        props.setProperty(KEY_CHECK_UPDATES_ON_STARTUP, String.valueOf(checkUpdatesOnStartup));
        save();
    }

    public boolean isCheckUpdatesOnStartupEnabled() {
        return Boolean.parseBoolean(props.getProperty(KEY_CHECK_UPDATES_ON_STARTUP, "true"));
    }

    public void setAutoStartEnabled(boolean autoStartEnabled) {
        props.setProperty(KEY_AUTO_START_ENABLED, String.valueOf(autoStartEnabled));
        save();
        AutoStartManager.setAutoStart(autoStartEnabled);
    }

    public boolean isAutoStartEnabled() {
        return Boolean.parseBoolean(props.getProperty(KEY_AUTO_START_ENABLED, "true"));
    }

    public void setHotkeyToStartApplication(List<Integer> keys) {
        var hotkey = emptyIfNull(keys).stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        props.setProperty(KEY_HOTKEY_TO_START_APPLICATION, hotkey);
        save();
    }

    public List<Integer> getHotkeyToStartApplication() {
        var hotkeyStr = props.getProperty(KEY_HOTKEY_TO_START_APPLICATION);
        if (hotkeyStr == null || hotkeyStr.isEmpty()) {
            return new ArrayList<>();
        }

        var cleanStr = hotkeyStr.replace("[", "")
                .replace("]", "")
                .replace(" ", "");

        return Arrays.stream(cleanStr.split(","))
                .filter(s -> !s.isEmpty())
                .map(Integer::parseInt)
                .collect(Collectors.toList());
    }
}