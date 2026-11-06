package to.sparkapp.app.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.Data;
import to.sparkapp.app.config.AppPreferences.WindowPlacement;

@Data
final class AppConfig {
    WindowPlacement windowPlacement;
    Boolean alwaysOnTop;
    Boolean reducedMotion;
    Boolean autoHide;
    Boolean topPill;
    Boolean topEdgeReveal;
    String lastUrl;
    String lastSiteId;
    Map<String, String> siteUrls;
    Boolean rememberLastAi;
    Double lastZoomValue;
    Boolean zoomEnabled;
    List<String> aiOrder;
    Boolean checkUpdatesOnStartup;
    Boolean autoStartEnabled;
    Boolean startApplicationHiddenEnabled;
    List<Integer> hotkeyToStartApplication;
    Boolean darkModeEnabled;
    Boolean reduceBackgroundMemory;

    boolean initializeDefaults() {
        boolean changed = false;

        if (rememberLastAi == null) {
            rememberLastAi = Boolean.parseBoolean(AppPreferencesKeys.REMEMBER_LAST_AI.getDefaultValue());
            changed = true;
        }

        if (lastZoomValue == null) {
            lastZoomValue = Double.parseDouble(AppPreferencesKeys.LAST_ZOOM_VALUE.getDefaultValue());
            changed = true;
        }

        if (zoomEnabled == null) {
            zoomEnabled = Boolean.parseBoolean(AppPreferencesKeys.ZOOM_ENABLED.getDefaultValue());
            changed = true;
        }

        if (aiOrder == null) {
            aiOrder = new ArrayList<>();
            changed = true;
        }

        if (checkUpdatesOnStartup == null) {
            checkUpdatesOnStartup = Boolean.parseBoolean(AppPreferencesKeys.CHECK_UPDATES_ON_STARTUP.getDefaultValue());
            changed = true;
        }

        if (autoStartEnabled == null) {
            autoStartEnabled = Boolean.parseBoolean(AppPreferencesKeys.AUTO_START_ENABLED.getDefaultValue());
            changed = true;
        }

        if (startApplicationHiddenEnabled == null) {
            startApplicationHiddenEnabled = Boolean.parseBoolean(
                    AppPreferencesKeys.START_APPLICATION_HIDDEN_ENABLED.getDefaultValue());
            changed = true;
        }

        if (hotkeyToStartApplication == null) {
            hotkeyToStartApplication = new ArrayList<>();
            changed = true;
        }

        if (darkModeEnabled == null) {
            darkModeEnabled = Boolean.parseBoolean(AppPreferencesKeys.DARK_MODE_ENABLED.getDefaultValue());
            changed = true;
        }

        return changed;
    }
}
