package to.sparkapp.app.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import to.sparkapp.app.config.AiConfiguration.AiConfig;
import to.sparkapp.app.utils.AutoStartManager;
import to.sparkapp.app.utils.UrlUtils;

@Slf4j
public class AppPreferences {

    private static final String FILE_NAME = "app-config.json";
    private final File file;

    private final ObjectMapper mapper = new ObjectMapper();
    private AppConfig config;
    private final SiteHistory history;

    private final ScheduledExecutorService saveScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        var t = new Thread(r, "preferences-save");
        t.setDaemon(true);

        return t;
    });
    private volatile ScheduledFuture<?> pendingDebouncedSave;

    public AppPreferences() {
        this(new File(AppPaths.DIR, FILE_NAME));
    }

    AppPreferences(File file) {
        this.file = file;
        mapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
        load();
        history = new SiteHistory(config, this::save, this::saveDebounced);

        if (config.initializeDefaults()) {
            save();
        }
    }

    private void load() {
        if (!file.exists() && !new File(file + ".bak").exists()) {
            config = new AppConfig();

            return;
        }

        try {
            config = JsonFiles.read(file, mapper.readerFor(AppConfig.class));

            if (config == null) {
                config = new AppConfig();
            }
        } catch (IOException e) {
            log.error("Failed to load application preferences", e);
            config = new AppConfig();
        }
    }

    /**
     * Immediate, synchronous write — used for settings that change rarely.
     */
    private synchronized void save() {
        file.getAbsoluteFile().getParentFile().mkdirs();

        try {
            JsonFiles.write(file, mapper, config);
        } catch (IOException e) {
            log.error("Failed to save application preferences", e);
        }
    }

    /**
     * Deferred write — coalesces multiple rapid calls into one disk write 500 ms after the last
     * mutation. Used for values that can change very frequently (current URL, zoom level).
     */
    private void saveDebounced() {
        if (pendingDebouncedSave != null) {
            pendingDebouncedSave.cancel(false);
        }

        pendingDebouncedSave = saveScheduler.schedule(this::save, 500, TimeUnit.MILLISECONDS);
    }

    /**
     * Writes any pending debounced changes straight away. Must be called before the process exits,
     * otherwise the last URL and zoom level are silently lost.
     */
    public synchronized void flush() {
        var pending = pendingDebouncedSave;

        if (pending != null && !pending.isDone()) {
            pending.cancel(false);
            save();
        }
    }

    public synchronized String getLastSiteId() {
        return config.lastSiteId;
    }

    public synchronized String getSiteUrl(AiConfig site) {
        return history.getSiteUrl(site);
    }

    public synchronized void rememberSitePage(AiConfig site, String url, boolean active) {
        history.rememberSitePage(site, url, active);
    }

    public synchronized void clearSiteHistory() {
        history.clearSiteHistory();
    }

    public synchronized void pruneSiteHistory(List<AiConfig> sites) {
        history.pruneSiteHistory(sites);
    }

    public synchronized String getLastUrl() {
        return config.lastUrl;
    }

    public synchronized void setRememberLastAi(boolean remember) {
        config.rememberLastAi = remember;
        save();

        if (!remember) {
            config.lastUrl = null;
            config.lastSiteId = null;
            config.siteUrls = new HashMap<>();
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
        return config.hotkeyToStartApplication != null ? new ArrayList<>(
                config.hotkeyToStartApplication) : new ArrayList<>();
    }

    public synchronized void setDarkModeEnabled(boolean darkModeEnabled) {
        config.darkModeEnabled = darkModeEnabled;
        save();
    }

    public synchronized boolean isBackgroundMemoryReductionEnabled() {
        return !Boolean.FALSE.equals(config.reduceBackgroundMemory);
    }

    public synchronized void setBackgroundMemoryReductionEnabled(boolean enabled) {
        config.reduceBackgroundMemory = enabled;
        save();
    }

    public synchronized boolean isDarkModeEnabled() {
        return Boolean.TRUE.equals(config.darkModeEnabled);
    }

    /**
     * Drops the remembered URL only when it no longer belongs to any configured provider. A deep
     * link such as {@code gemini.google.com/app/<id>} is kept, so the app reopens on the exact
     * conversation the user left.
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

    public record WindowPlacement(boolean docked, double x, double y, double width, double height, double manualWidth,
                                  double manualHeight, double monitorX, double monitorY) {
    }

    public synchronized WindowPlacement getWindowPlacement() {
        return config.windowPlacement;
    }

    public synchronized void setWindowPlacement(WindowPlacement placement) {
        config.windowPlacement = placement;
        saveDebounced();
    }

    public synchronized boolean isAlwaysOnTop() {
        return !Boolean.FALSE.equals(config.alwaysOnTop);
    }

    public synchronized void setAlwaysOnTop(boolean enabled) {
        config.alwaysOnTop = enabled;
        saveDebounced();
    }

    public synchronized boolean isReducedMotion() {
        return Boolean.TRUE.equals(config.reducedMotion);
    }

    public synchronized boolean isAutoHide() {
        return Boolean.TRUE.equals(config.autoHide);
    }

    public synchronized void setAutoHide(boolean enabled) {
        config.autoHide = enabled;
        saveDebounced();
    }

    public synchronized boolean isTopPill() {
        return !Boolean.FALSE.equals(config.topPill);
    }

    public synchronized void setTopPill(boolean enabled) {
        config.topPill = enabled;
        saveDebounced();
    }

    public synchronized boolean isTopEdgeReveal() {
        return !Boolean.FALSE.equals(config.topEdgeReveal);
    }

    public synchronized void setTopEdgeReveal(boolean enabled) {
        config.topEdgeReveal = enabled;
        saveDebounced();
    }

    public synchronized void setReducedMotion(boolean enabled) {
        config.reducedMotion = enabled;
        saveDebounced();
    }
}
