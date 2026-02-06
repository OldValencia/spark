package io.loom.app.config;

import io.loom.app.utils.AutoStartManager;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.apache.commons.collections4.ListUtils.emptyIfNull;

@Slf4j
public class AppPreferences {

    private static final String FILE_NAME = "settings.properties";
    private static final String DIR = System.getProperty("user.home") + File.separator + ".loom";
    private static final File FILE = new File(DIR, FILE_NAME);

    private final Properties props = new Properties();

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

        for (var key : AppPreferencesKeys.values()) {
            if (Objects.equals(key.getDefaultValue(), "<skip_default>")) {
                continue;
            }
            if (!props.containsKey(key.getKey())) {
                props.setProperty(key.getKey(), key.getDefaultValue());
                changed = true;
            }
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
            props.setProperty(AppPreferencesKeys.LAST_URL.getKey(), url);
            save();
        }
    }

    public String getLastUrl() {
        return props.getProperty(AppPreferencesKeys.LAST_URL.getKey());
    }

    public void setRememberLastAi(boolean remember) {
        props.setProperty(AppPreferencesKeys.REMEMBER_LAST_AI.getKey(), String.valueOf(remember));
        save();
        if (!remember) {
            props.remove(AppPreferencesKeys.LAST_URL.getKey());
            save();
        }
    }

    public boolean isRememberLastAi() {
        return Boolean.parseBoolean(props.getProperty(AppPreferencesKeys.REMEMBER_LAST_AI.getKey(), AppPreferencesKeys.REMEMBER_LAST_AI.getDefaultValue()));
    }

    public void setLastZoomValue(Double zoomValue) {
        if (isZoomEnabled()) {
            props.setProperty(AppPreferencesKeys.LAST_ZOOM_VALUE.getKey(), String.valueOf(zoomValue));
            save();
        }
    }

    public Double getLastZoomValue() {
        try {
            return Double.valueOf(props.getProperty(AppPreferencesKeys.LAST_ZOOM_VALUE.getKey(), AppPreferencesKeys.LAST_ZOOM_VALUE.getDefaultValue()));
        } catch (NumberFormatException e) {
            return Double.valueOf(AppPreferencesKeys.LAST_ZOOM_VALUE.getDefaultValue());
        }
    }

    public void setZoomEnabled(boolean zoomEnabled) {
        props.setProperty(AppPreferencesKeys.ZOOM_ENABLED.getKey(), String.valueOf(zoomEnabled));
        save();
        if (!zoomEnabled) {
            setLastZoomValue(Double.valueOf(AppPreferencesKeys.LAST_ZOOM_VALUE.getDefaultValue()));
        }
    }

    public boolean isZoomEnabled() {
        return Boolean.parseBoolean(props.getProperty(AppPreferencesKeys.ZOOM_ENABLED.getKey(), AppPreferencesKeys.ZOOM_ENABLED.getDefaultValue()));
    }

    public void setAiOrder(List<String> urls) {
        var order = String.join(",", urls);
        props.setProperty(AppPreferencesKeys.AI_ORDER.getKey(), order);
        save();
    }

    public List<String> getAiOrder() {
        var orderStr = props.getProperty(AppPreferencesKeys.AI_ORDER.getKey());
        if (orderStr == null || orderStr.isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.stream(orderStr.split(","))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    public void setCheckUpdatesOnStartup(boolean checkUpdatesOnStartup) {
        props.setProperty(AppPreferencesKeys.CHECK_UPDATES_ON_STARTUP.getKey(), String.valueOf(checkUpdatesOnStartup));
        save();
    }

    public boolean isCheckUpdatesOnStartupEnabled() {
        return Boolean.parseBoolean(props.getProperty(AppPreferencesKeys.CHECK_UPDATES_ON_STARTUP.getKey(), AppPreferencesKeys.CHECK_UPDATES_ON_STARTUP.getDefaultValue()));
    }

    public void setAutoStartEnabled(boolean autoStartEnabled) {
        props.setProperty(AppPreferencesKeys.AUTO_START_ENABLED.getKey(), String.valueOf(autoStartEnabled));
        save();
        AutoStartManager.setAutoStart(autoStartEnabled);
    }

    public boolean isAutoStartEnabled() {
        return Boolean.parseBoolean(props.getProperty(AppPreferencesKeys.AUTO_START_ENABLED.getKey(), AppPreferencesKeys.AUTO_START_ENABLED.getDefaultValue()));
    }

    public void setStartApplicationHiddenEnabled(boolean autoStartEnabled) {
        props.setProperty(AppPreferencesKeys.START_APPLICATION_HIDDEN_ENABLED.getKey(), String.valueOf(autoStartEnabled));
        save();
    }

    public boolean isStartApplicationHiddenEnabled() {
        return Boolean.parseBoolean(props.getProperty(AppPreferencesKeys.START_APPLICATION_HIDDEN_ENABLED.getKey(), AppPreferencesKeys.START_APPLICATION_HIDDEN_ENABLED.getDefaultValue()));
    }

    public void setHotkeyToStartApplication(List<Integer> keys) {
        var hotkey = emptyIfNull(keys).stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        props.setProperty(AppPreferencesKeys.HOTKEY_TO_START_APPLICATION.getKey(), hotkey);
        save();
    }

    public List<Integer> getHotkeyToStartApplication() {
        var hotkeyStr = props.getProperty(AppPreferencesKeys.HOTKEY_TO_START_APPLICATION.getKey());
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

    public void setDarkModeEnabled(boolean darkModeEnabled) {
        props.setProperty(AppPreferencesKeys.DARK_MODE_ENABLED.getKey(), String.valueOf(darkModeEnabled));
        save();
    }

    public boolean isDarkModeEnabled() {
        return Boolean.parseBoolean(props.getProperty(AppPreferencesKeys.DARK_MODE_ENABLED.getKey(), AppPreferencesKeys.DARK_MODE_ENABLED.getDefaultValue()));
    }
}