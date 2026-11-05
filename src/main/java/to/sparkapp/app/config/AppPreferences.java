package to.sparkapp.app.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import to.sparkapp.app.utils.AutoStartManager;
import to.sparkapp.app.utils.UrlUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
public class AppPreferences {

    private static final String FILE_NAME = "app-config.json";
    private static final File FILE = new File(AppPaths.DIR, FILE_NAME);

    private final ObjectMapper mapper = new ObjectMapper();
    private AppConfig config;

    private final ScheduledExecutorService saveScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        var t = new Thread(r, "preferences-save");
        t.setDaemon(true);
        return t;
    });
    private volatile ScheduledFuture<?> pendingDebouncedSave;

    public AppPreferences() {
        mapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
        load();
        initDefaults();
    }

    private void load() {
        if (!FILE.exists() && !new File(FILE + ".bak").exists()) {
            config = new AppConfig();
            return;
        }

        try {
            config = JsonFiles.read(FILE, mapper.readerFor(AppConfig.class));
            if (config == null) config = new AppConfig();
        } catch (IOException e) {
            log.error("Failed to load application preferences", e);
            config = new AppConfig();
        }
    }

    private void initDefaults() {
        boolean changed = false;

        if (config.rememberLastAi == null) {
            config.rememberLastAi = Boolean.parseBoolean(AppPreferencesKeys.REMEMBER_LAST_AI.getDefaultValue());
            changed = true;
        }
        if (config.lastZoomValue == null) {
            config.lastZoomValue = Double.parseDouble(AppPreferencesKeys.LAST_ZOOM_VALUE.getDefaultValue());
            changed = true;
        }
        if (config.zoomEnabled == null) {
            config.zoomEnabled = Boolean.parseBoolean(AppPreferencesKeys.ZOOM_ENABLED.getDefaultValue());
            changed = true;
        }
        if (config.aiOrder == null) {
            config.aiOrder = new ArrayList<>();
            changed = true;
        }
        if (config.checkUpdatesOnStartup == null) {
            config.checkUpdatesOnStartup = Boolean.parseBoolean(AppPreferencesKeys.CHECK_UPDATES_ON_STARTUP.getDefaultValue());
            changed = true;
        }
        if (config.autoStartEnabled == null) {
            config.autoStartEnabled = Boolean.parseBoolean(AppPreferencesKeys.AUTO_START_ENABLED.getDefaultValue());
            changed = true;
        }
        if (config.startApplicationHiddenEnabled == null) {
            config.startApplicationHiddenEnabled = Boolean.parseBoolean(AppPreferencesKeys.START_APPLICATION_HIDDEN_ENABLED.getDefaultValue());
            changed = true;
        }
        if (config.hotkeyToStartApplication == null) {
            config.hotkeyToStartApplication = new ArrayList<>();
            changed = true;
        }
        if (config.darkModeEnabled == null) {
            config.darkModeEnabled = Boolean.parseBoolean(AppPreferencesKeys.DARK_MODE_ENABLED.getDefaultValue());
            changed = true;
        }

        if (changed) {
            save();
        }
    }

    /**
     * Immediate, synchronous write — used for settings that change rarely.
     */
    private synchronized void save() {
        new File(AppPaths.DIR).mkdirs();
        try {
            JsonFiles.write(FILE, mapper, config);
        } catch (IOException e) {
            log.error("Failed to save application preferences", e);
        }
    }

    /**
     * Deferred write — coalesces multiple rapid calls into one disk write
     * 500 ms after the last mutation.  Used for values that can change very
     * frequently (current URL, zoom level).
     */
    private void saveDebounced() {
        if (pendingDebouncedSave != null) {
            pendingDebouncedSave.cancel(false);
        }
        pendingDebouncedSave = saveScheduler.schedule(this::save, 500, TimeUnit.MILLISECONDS);
    }

    /**
     * Writes any pending debounced changes straight away. Must be called before the
     * process exits, otherwise the last URL and zoom level are silently lost.
     */
    public synchronized void flush() {
        var pending = pendingDebouncedSave;
        if (pending != null && !pending.isDone()) {
            pending.cancel(false);
            save();
        }
    }

    // Last-URL — debounced because the webview fires a URL-change event for
    // every in-app navigation (e.g. chatgpt.com → chatgpt.com/c/<uuid>).
    public synchronized void setLastUrl(String url) {
        if (Boolean.TRUE.equals(config.rememberLastAi)) {
            config.lastUrl = url;
            saveDebounced();
        }
    }

    public synchronized String getLastUrl() {
        return config.lastUrl;
    }

    public synchronized void setRememberLastAi(boolean remember) {
        config.rememberLastAi = remember;
        save();
        if (!remember) {
            config.lastUrl = null;
            save();
        }
    }

    public synchronized boolean isRememberLastAi() {
        return Boolean.TRUE.equals(config.rememberLastAi);
    }

    public synchronized void setLastZoomValue(Double zoomValue) {
        if (Boolean.TRUE.equals(config.zoomEnabled)) {
            config.lastZoomValue = zoomValue;
            saveDebounced();
        }
    }

    public synchronized Double getLastZoomValue() {
        return config.lastZoomValue != null ? config.lastZoomValue : 0.0;
    }

    public synchronized void setZoomEnabled(boolean zoomEnabled) {
        config.zoomEnabled = zoomEnabled;
        if (!zoomEnabled) {
            // Written directly: setLastZoomValue() is a no-op once zoom is disabled.
            config.lastZoomValue = 0.0;
        }
        save();
    }

    public synchronized boolean isZoomEnabled() {
        return Boolean.TRUE.equals(config.zoomEnabled);
    }

    public synchronized void setAiOrder(List<String> urls) {
        config.aiOrder = urls != null ? new ArrayList<>(urls) : new ArrayList<>();
        save();
    }

    public synchronized List<String> getAiOrder() {
        return config.aiOrder != null ? new ArrayList<>(config.aiOrder) : new ArrayList<>();
    }

    public synchronized void setCheckUpdatesOnStartup(boolean checkUpdatesOnStartup) {
        config.checkUpdatesOnStartup = checkUpdatesOnStartup;
        save();
    }

    public synchronized boolean isCheckUpdatesOnStartupEnabled() {
        return Boolean.TRUE.equals(config.checkUpdatesOnStartup);
    }

    public synchronized void setAutoStartEnabled(boolean autoStartEnabled) {
        config.autoStartEnabled = autoStartEnabled;
        save();
        AutoStartManager.setAutoStart(autoStartEnabled);
    }

    public synchronized boolean isAutoStartEnabled() {
        return Boolean.TRUE.equals(config.autoStartEnabled);
    }

    public synchronized void setStartApplicationHiddenEnabled(boolean enabled) {
        config.startApplicationHiddenEnabled = enabled;
        save();
    }

    public synchronized boolean isStartApplicationHiddenEnabled() {
        return Boolean.TRUE.equals(config.startApplicationHiddenEnabled);
    }

    public synchronized void setHotkeyToStartApplication(List<Integer> keys) {
        config.hotkeyToStartApplication = keys != null ? new ArrayList<>(keys) : new ArrayList<>();
        save();
    }

    public synchronized List<Integer> getHotkeyToStartApplication() {
        return config.hotkeyToStartApplication != null
                ? new ArrayList<>(config.hotkeyToStartApplication)
                : new ArrayList<>();
    }

    public synchronized void setDarkModeEnabled(boolean darkModeEnabled) {
        config.darkModeEnabled = darkModeEnabled;
        save();
    }

    public synchronized boolean isDarkModeEnabled() {
        return Boolean.TRUE.equals(config.darkModeEnabled);
    }

    /**
     * Drops the remembered URL only when it no longer belongs to any configured
     * provider. A deep link such as {@code gemini.google.com/app/<id>} is kept, so
     * the app reopens on the exact conversation the user left.
     */
    public synchronized void cleanupLastUrlIfNeeded(List<String> validUrls) {
        if (config.lastUrl == null || validUrls.contains(config.lastUrl)) {
            return;
        }

        if (UrlUtils.matchingBaseUrl(config.lastUrl, validUrls) != null) {
            return;
        }

        log.info("Removing lastUrl of an unknown provider: {}", UrlUtils.forLog(config.lastUrl));
        config.lastUrl = null;
        save();
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
