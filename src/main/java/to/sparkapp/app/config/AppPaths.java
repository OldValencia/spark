package to.sparkapp.app.config;

import to.sparkapp.app.utils.SystemUtils;
import java.io.File;

public class AppPaths {

    public static final String DIR;
    public static final File DATA_DIR;

    static {
        boolean isDev = "Dev-Build".equals(SystemUtils.VERSION) || SystemUtils.VERSION == null;

        if (SystemUtils.isMac() && !isDev) {
            DIR = System.getProperty("user.home") + "/Library/Application Support/OldValencia/Spark";
        } else {
            DIR = System.getProperty("user.home") + "/Documents/OldValencia/Spark";
        }

        DATA_DIR = new File(DIR);
        DATA_DIR.mkdirs();
    }
}