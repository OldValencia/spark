package to.sparkapp.app.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import to.sparkapp.app.utils.AutoStartManager;
import to.sparkapp.app.utils.SystemUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class AppPreferences {

    public static final String DIR;
    public static final File DATA_DIR;

    static {
        boolean isDev = "Dev-Build".equals(SystemUtils.VERSION) || SystemUtils.VERSION == null;

        if (SystemUtils.isMac() && !isDev) {
            DIR = System.getProperty("user.home") + "/Library/Application Support/OldValencia/Spark";
        } else {
            DIR = System.getProperty("user.home") + "/Documents/OldValencia/Spark";
        }

        DATA_DIR = new File(DIR);
        DATA_DIR.mkdirs();
    }

    private static final String FILE_NAME = "app-config.json";
    private static final File FILE = new File(DIR, FILE_NAME);

    private final ObjectMapper mapper = new ObjectMapper();
    private AppConfig config;

    public AppPreferences() {
        mapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
        load();
        initDefaults();
    }

    private void load() {
        if (!FILE.exists()) {
            config = new AppConfig();
            return;
        }

        try {
            config = mapper.readValue(FILE, AppConfig.class);
        } catch (IOException e) {
            log.error("Failed to load application preferences", e);
            config = new AppConfig();
        }
    }

    private void initDefaults() {
        boolean changed = false;

        if (config.rememberLastAi == null) {
            config.rememberLastAi = true;
            changed = true;
        }
        if (config.lastZoomValue == null) {
            config.lastZoomValue = 0.0;
            changed = true;
        }
        if (config.zoomEnabled == null) {
            config.zoomEnabled = true;
            changed = true;
        }
        if (config.aiOrder == null) {
            config.aiOrder = new ArrayList<>();
            changed = true;
        }
        if (config.checkUpdatesOnStartup == null) {
            config.checkUpdatesOnStartup = true;
            changed = true;
        }
        if (config.autoStartEnabled == null) {
            config.autoStartEnabled = true;
            changed = true;
        }
        if (config.startApplicationHiddenEnabled == null) {
            config.startApplicationHiddenEnabled = false;
            changed = true;
        }
        if (config.hotkeyToStartApplication == null) {
            config.hotkeyToStartApplication = new ArrayList<>();
            changed = true;
        }
        if (config.darkModeEnabled == null) {
            config.darkModeEnabled = true;
            changed = true;
        }

        if (changed) {
            save();
        }
    }

    private void save() {
        new File(DIR).mkdirs();
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(FILE, config);
        } catch (IOException e) {
            log.error("Failed to save application preferences", e);
        }
    }

    public void setLastUrl(String url) {
        if (Boolean.TRUE.equals(config.rememberLastAi)) {
            config.lastUrl = url;
            save();
        }
    }

    public String getLastUrl() {
        return config.lastUrl;
    }

    public void setRememberLastAi(boolean remember) {
        config.rememberLastAi = remember;
        save();
        if (!remember) {
            config.lastUrl = null;
            save();
        }
    }

    public boolean isRememberLastAi() {
        return Boolean.TRUE.equals(config.rememberLastAi);
    }

    public void setLastZoomValue(Double zoomValue) {
        if (Boolean.TRUE.equals(config.zoomEnabled)) {
            config.lastZoomValue = zoomValue;
            save();
        }
    }

    public Double getLastZoomValue() {
        return config.lastZoomValue != null ? config.lastZoomValue : 0.0;
    }

    public void setZoomEnabled(boolean zoomEnabled) {
        config.zoomEnabled = zoomEnabled;
        save();
        if (!zoomEnabled) {
            setLastZoomValue(0.0);
        }
    }

    public boolean isZoomEnabled() {
        return Boolean.TRUE.equals(config.zoomEnabled);
    }

    public void setAiOrder(List<String> urls) {
        config.aiOrder = urls != null ? new ArrayList<>(urls) : new ArrayList<>();
        save();
    }

    public List<String> getAiOrder() {
        return config.aiOrder != null ? new ArrayList<>(config.aiOrder) : new ArrayList<>();
    }

    public void setCheckUpdatesOnStartup(boolean checkUpdatesOnStartup) {
        config.checkUpdatesOnStartup = checkUpdatesOnStartup;
        save();
    }

    public boolean isCheckUpdatesOnStartupEnabled() {
        return Boolean.TRUE.equals(config.checkUpdatesOnStartup);
    }

    public void setAutoStartEnabled(boolean autoStartEnabled) {
        config.autoStartEnabled = autoStartEnabled;
        save();
        AutoStartManager.setAutoStart(autoStartEnabled);
    }

    public boolean isAutoStartEnabled() {
        return Boolean.TRUE.equals(config.autoStartEnabled);
    }

    public void setStartApplicationHiddenEnabled(boolean autoStartEnabled) {
        config.startApplicationHiddenEnabled = autoStartEnabled;
        save();
    }

    public boolean isStartApplicationHiddenEnabled() {
        return Boolean.TRUE.equals(config.startApplicationHiddenEnabled);
    }

    public void setHotkeyToStartApplication(List<Integer> keys) {
        config.hotkeyToStartApplication = keys != null ? new ArrayList<>(keys) : new ArrayList<>();
        save();
    }

    public List<Integer> getHotkeyToStartApplication() {
        return config.hotkeyToStartApplication != null ?
                new ArrayList<>(config.hotkeyToStartApplication) : new ArrayList<>();
    }

    public void setDarkModeEnabled(boolean darkModeEnabled) {
        config.darkModeEnabled = darkModeEnabled;
        save();
    }

    public boolean isDarkModeEnabled() {
        return Boolean.TRUE.equals(config.darkModeEnabled);
    }

    public void cleanupLastUrlIfNeeded(List<String> validUrls) {
        if (config.lastUrl != null && !validUrls.contains(config.lastUrl)) {
            log.info("Removing invalid lastUrl: {}", config.lastUrl);
            config.lastUrl = null;
            save();
        }
    }

    @Data
    private static class AppConfig {
        private String lastUrl;
        private Boolean rememberLastAi;
        private Double lastZoomValue;
        private Boolean zoomEnabled;
        private List<String> aiOrder;
        private Boolean checkUpdatesOnStartup;
        private Boolean autoStartEnabled;
        private Boolean startApplicationHiddenEnabled;
        private List<Integer> hotkeyToStartApplication;
        private Boolean darkModeEnabled;
    }
}
