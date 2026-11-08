package to.sparkapp.app.config;

import java.io.File;

/** Opens preferences stored in a test file instead of the user's Spark folder. */
public final class TestPreferences {
    private TestPreferences() {
    }

    public static AppPreferences at(File file) {
        return new AppPreferences(file);
    }
}
