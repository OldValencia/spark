package to.sparkapp.app.extensions;

import java.util.Optional;
import java.util.regex.Pattern;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Recognizes Chrome Web Store extension IDs in pasted text, such as a store page address. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ChromeWebStore {
    static final String UPDATE_URL = "https://clients2.google.com/service/update2/crx";
    private static final Pattern ID = Pattern.compile("(?<![a-z])([a-p]{32})(?![a-z])");

    /** Finds the 32-letter extension ID in a store link or a bare ID. */
    public static Optional<String> extensionId(String text) {
        if (text == null) {
            return Optional.empty();
        }

        var matcher = ID.matcher(text.trim().toLowerCase());
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }
}
