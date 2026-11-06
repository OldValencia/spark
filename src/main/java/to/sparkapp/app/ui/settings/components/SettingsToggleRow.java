package to.sparkapp.app.ui.settings.components;

import java.util.function.Consumer;

import lombok.Getter;

public final class SettingsToggleRow extends SettingsRow {
    @Getter
    private final AnimatedToggleSwitch toggle;

    public SettingsToggleRow(String title, String description, boolean initialValue, Consumer<Boolean> onChange) {
        this(title, description, new AnimatedToggleSwitch(initialValue), onChange);
    }

    private SettingsToggleRow(String title, String description, AnimatedToggleSwitch toggle, Consumer<Boolean> onChange) {
        super(title, description, toggle);
        this.toggle = toggle;
        toggle.setAccessibleText(title);
        toggle.setOnChange(onChange);
        disabledProperty().addListener((observable, previous, disabled) -> toggle.setOpacity(disabled ? 0.5 : 1));
    }
}
