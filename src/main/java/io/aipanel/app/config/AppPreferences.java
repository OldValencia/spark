package io.aipanel.app.config;

import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.util.Properties;

@Slf4j
public class AppPreferences {

    private static final String FILE_NAME = "settings.properties";
    private static final String DIR = System.getProperty("user.home") + File.separator + ".aipanel";
    private static final File FILE = new File(DIR, FILE_NAME);

    private final Properties props = new Properties();

    private static final String KEY_LAST_URL = "last_url";
    private static final String KEY_REMEMBER_AI = "remember_last_ai";
    private static final String KEY_LAST_ZOOM_VALUE = "last_zoom_value";
    private static final String KEY_ZOOM_ENABLED = "zoom_enabled";

    public AppPreferences() {
        load();
    }

    private void load() {
        if (!FILE.exists()) return;
        try (var is = new FileInputStream(FILE)) {
            props.load(is);
        } catch (IOException e) {
            log.error("Failed to load application preferences", e);
        }
    }

    private void save() {
        new File(DIR).mkdirs();
        try (var os = new FileOutputStream(FILE)) {
            props.store(os, "AI Panel Settings");
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
        return Double.valueOf(props.getProperty(KEY_LAST_ZOOM_VALUE));
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
}
