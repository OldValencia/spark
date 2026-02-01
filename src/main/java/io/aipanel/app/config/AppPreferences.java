package io.aipanel.app.config;

import java.io.*;
import java.util.Properties;

public class AppPreferences {

    private static final String FILE_NAME = "settings.properties";
    private static final String DIR = System.getProperty("user.home") + File.separator + ".aipanel";
    private static final File FILE = new File(DIR, FILE_NAME);

    private final Properties props = new Properties();

    // Ключи
    private static final String KEY_LAST_URL = "last_url";
    private static final String KEY_REMEMBER_AI = "remember_last_ai";

    public AppPreferences() {
        load();
    }

    private void load() {
        if (!FILE.exists()) return;
        try (var is = new FileInputStream(FILE)) {
            props.load(is);
        } catch (IOException e) {
            e.printStackTrace(); // fixme add logs
        }
    }

    private void save() {
        new File(DIR).mkdirs();
        try (var os = new FileOutputStream(FILE)) {
            props.store(os, "AI Panel Settings");
        } catch (IOException e) {
            e.printStackTrace(); // fixme add logs
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
        // Если выключили запоминание, стираем последний URL
        if (!remember) {
            props.remove(KEY_LAST_URL);
            save();
        }
    }

    public boolean isRememberLastAi() {
        // По умолчанию true
        return Boolean.parseBoolean(props.getProperty(KEY_REMEMBER_AI, "true"));
    }
}
