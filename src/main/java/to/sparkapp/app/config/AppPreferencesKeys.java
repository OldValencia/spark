package to.sparkapp.app.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AppPreferencesKeys {

    LAST_URL("<skip_default>"),
    REMEMBER_LAST_AI("true"),
    LAST_ZOOM_VALUE("0.0"),
    ZOOM_ENABLED("true"),
    AI_ORDER(""),
    CHECK_UPDATES_ON_STARTUP("true"),
    AUTO_START_ENABLED("true"),
    START_APPLICATION_HIDDEN_ENABLED("false"),
    HOTKEY_TO_START_APPLICATION(""),
    DARK_MODE_ENABLED("true");

    private final String defaultValue;
}
