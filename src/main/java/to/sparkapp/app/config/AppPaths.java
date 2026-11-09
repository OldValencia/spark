package to.sparkapp.app.config;

import java.io.File;

import to.sparkapp.app.utils.SystemUtils;

public class AppPaths {

    public static final String DIR;
    public static final File DATA_DIR;

    static {
        boolean isDev = "Dev-Build".equals(SystemUtils.VERSION) || SystemUtils.VERSION == null;

        var override = System.getProperty("spark.dataDir");

        if (override != null && !override.isBlank()) {
            DIR = new File(override).getAbsolutePath();
        } else if (SystemUtils.isMac() && !isDev) {
            DIR = System.getProperty("user.home") + "/Library/Application Support/OldValencia/Spark";
        } else {
            DIR = System.getProperty("user.home") + "/Documents/OldValencia/Spark";
        }

        DATA_DIR = new File(DIR);
        DATA_DIR.mkdirs();
    }

    /**
     * The settings folder as a person would type it: %USERPROFILE% on Windows and ~ elsewhere, so
     * it also fits another user's PC.
     */
    public static String displayDir() {
        var home = System.getProperty("user.home");

        if (!DIR.startsWith(home)) {
            return DIR;
        }

        var rest = DIR.substring(home.length());

        if (SystemUtils.isWindows()) {
            return "%USERPROFILE%" + rest.replace('/', '\\');
        }

        return "~" + rest;
    }
}
