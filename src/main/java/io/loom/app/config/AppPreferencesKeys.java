package io.loom.app.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AppPreferencesKeys {

    LAST_URL("last_url", "<skip_default>"),
    REMEMBER_LAST_AI("remember_last_ai", "true"),
    LAST_ZOOM_VALUE("last_zoom_value", "0.0"),
    ZOOM_ENABLED("zoom_enabled", "true"),
    AI_ORDER("ai_order", ""),
    CHECK_UPDATES_ON_STARTUP("check_updates_on_startup", "true"),
    AUTO_START_ENABLED("auto_start_enabled", "true"),
    START_APPLICATION_HIDDEN_ENABLED("start_application_hidden_enabled", "false"),
    HOTKEY_TO_START_APPLICATION("hotkey_to_start_application", ""),
    DARK_MODE_ENABLED("dark_mode_enabled", "true");

    private final String key;
    private final String defaultValue;
}
