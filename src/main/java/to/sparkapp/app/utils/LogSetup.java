package to.sparkapp.app.utils;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import to.sparkapp.app.config.AppPaths;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class LogSetup {

    private static final int MAX_LOG_FILES = 3;

    public static void init() {
        var logsDirPath = new File(AppPaths.DATA_DIR, "logs").getAbsolutePath();
        var logsDir = new File(logsDirPath);

        if (!logsDir.exists()) {
            logsDir.mkdirs();
        }
        cleanOldLogs(logsDir);

        var timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        var logFileName = "app-" + timestamp;

        System.setProperty("log.name", logFileName);
        System.setProperty("log.dir", logsDirPath);

        reloadLogbackConfig();
        System.out.println("Logs initialized at: " + new File(logsDir, logFileName + ".log").getAbsolutePath());
    }

    private static void reloadLogbackConfig() {
        var context = (LoggerContext) LoggerFactory.getILoggerFactory();

        try {
            var configurator = new JoranConfigurator();
            configurator.setContext(context);
            context.reset();

            var configFile = LogSetup.class.getResource("/app-logback.xml");
            if (configFile != null) {
                configurator.doConfigure(configFile);
            }
        } catch (JoranException e) {
            System.out.println("It's impossible to reload config " + e.getMessage());
        }
    }

    private static void cleanOldLogs(File dir) {
        File[] files = dir.listFiles((d, name) -> name.startsWith("app-") && name.endsWith(".log"));

        if (files != null && files.length >= MAX_LOG_FILES) {
            Arrays.sort(files, Comparator.comparingLong(File::lastModified));
            int filesToDelete = files.length - (MAX_LOG_FILES - 1);
            for (int i = 0; i < filesToDelete; i++) {
                if (files[i].delete()) {
                    System.out.println("Deleted old log: " + files[i].getName());
                }
            }
        }
    }
}
