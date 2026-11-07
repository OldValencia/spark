package to.sparkapp.app.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

public record WindowSizePreset(String name, int width, int height) {
    public static final int MAX_NAME_LENGTH = 32;

    public WindowSizePreset {
        name = name == null ? "" : name.strip();
    }

    @JsonIgnore
    public boolean isValid() {
        return !name.isBlank() && name.length() <= MAX_NAME_LENGTH &&
                name.codePoints().noneMatch(Character::isISOControl) && width > 0 && height > 0;
    }

    @Getter
    @RequiredArgsConstructor
    public enum Slot {
        SMALL(new WindowSizePreset("S", 700, 580)),
        MEDIUM(new WindowSizePreset("M", 860, 700)),
        LARGE(new WindowSizePreset("L", 1120, 820));

        private final WindowSizePreset defaultPreset;
    }
}
