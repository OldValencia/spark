package io.loom.app.utils;

import lombok.extern.slf4j.Slf4j;
import org.cef.browser.CefBrowser;

import javax.swing.Timer;
import java.io.File;

import static io.loom.app.utils.LogSetup.LOGS_DIR;

@Slf4j
public class MemoryMonitor {

    private static final long HIGH_MEMORY_THRESHOLD = 400 * 1024 * 1024; // 400 MB
    private static final int CHECK_INTERVAL = 60000;

    private final CefBrowser browser;
    private final Timer timer;

    public MemoryMonitor(CefBrowser browser) {
        this.browser = browser;
        this.timer = new Timer(CHECK_INTERVAL, e -> checkMemory());
    }

    public void start() {
        timer.start();
        log.info("Memory monitoring started");
    }

    public void stop() {
        timer.stop();
    }

    private void checkMemory() {
        var runtime = Runtime.getRuntime();
        var totalMemory = runtime.totalMemory();
        var freeMemory = runtime.freeMemory();
        var usedMemory = totalMemory - freeMemory;
        var maxMemory = runtime.maxMemory();
        var usedPercent = (usedMemory * 100.0) / maxMemory;

        log.debug("Memory: {} MB / {} MB ({}%)",
                usedMemory / 1024 / 1024,
                maxMemory / 1024 / 1024,
                String.format("%.1f", usedPercent)
        );

        if (usedMemory > HIGH_MEMORY_THRESHOLD) {
            log.warn("High memory usage detected: {} MB", usedMemory / 1024 / 1024);
            performCleanup();
        }
    }

    private void performCleanup() {
        log.info("Performing memory cleanup...");

        System.gc();

        if (browser != null && !browser.isLoading()) {
            browser.executeJavaScript(
                    "if (window.gc) { window.gc(); }",
                    "", 0
            );
        }

        clearApplicationTrash();

        var runtime = Runtime.getRuntime();
        var afterCleanup = runtime.totalMemory() - runtime.freeMemory();
        log.info("Memory after cleanup: {} MB", afterCleanup / 1024 / 1024);
    }

    private void clearApplicationTrash() {
        var logsDir = new File(LOGS_DIR);
        LogSetup.rotateCefLog(logsDir);
    }

    public void logMemoryStats() {
        var runtime = Runtime.getRuntime();
        var total = runtime.totalMemory() / 1024 / 1024;
        var free = runtime.freeMemory() / 1024 / 1024;
        var used = total - free;
        var max = runtime.maxMemory() / 1024 / 1024;

        log.info("=== Memory Statistics ===");
        log.info("Used:  {} MB", used);
        log.info("Free:  {} MB", free);
        log.info("Total: {} MB", total);
        log.info("Max:   {} MB", max);
        log.info("========================");
    }
}