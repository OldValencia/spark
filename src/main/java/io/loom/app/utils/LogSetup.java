package io.loom.app.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class LogSetup {

    public static final String APP_DIR = System.getProperty("user.home") + File.separator + ".loom";
    public static final String LOGS_DIR = APP_DIR + File.separator + "logs";
    private static final int MAX_LOG_FILES = 3;
    private static final long CEF_LOG_MAX_BYTES = 2 * 1024 * 1024; // 2 MB

    public static void init() {
        var logsDir = new File(LOGS_DIR);
        if (!logsDir.exists()) {
            logsDir.mkdirs();
        }
        cleanOldLogs(logsDir);
        rotateCefLog(logsDir);

        var timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        var logFileName = "app-" + timestamp;
        System.setProperty("log.name", logFileName);
    }

    static void rotateCefLog(File dir) {
        var cefLog = new File(dir, "cef.log");
        if (cefLog.exists() && cefLog.length() > CEF_LOG_MAX_BYTES) {
            if (cefLog.delete()) {
                System.out.println("Rotated cef.log (exceeded " + (CEF_LOG_MAX_BYTES / 1024) + " KB)");
            }
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