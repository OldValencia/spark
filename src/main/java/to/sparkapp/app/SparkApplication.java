package to.sparkapp.app;

import javafx.application.Application;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import to.sparkapp.app.config.AiConfiguration;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.utils.LogSetup;
import to.sparkapp.app.utils.SingleInstanceLock;
import to.sparkapp.app.windows.MainWindow;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.util.logging.Level;

@Slf4j
public class SparkApplication extends Application {

    private static Logger initLog;

    public static void main(String[] args) {
        LogSetup.init();
        initLog = LoggerFactory.getLogger(SparkApplication.class);

        suppressNoiseJulLoggers();

        if (!SingleInstanceLock.tryAcquire()) {
            initLog.info("Another instance is already running — exiting");
            System.exit(0);
            return;
        }

        Thread.setDefaultUncaughtExceptionHandler(new GlobalExceptionHandler());
        setupCookies();
        launch(args);
        SingleInstanceLock.release();
    }

    /**
     * Mutes JUL loggers that produce noise but are not actionable.
     * WCMediaPlayerImpl spams warnings when a site tries to play audio/video
     * formats that JavaFX WebView does not support (e.g. mp3, data URIs).
     */
    private static void suppressNoiseJulLoggers() {
        java.util.logging.Logger.getLogger("com.sun.javafx.webkit.prism.WCMediaPlayerImpl")
                .setLevel(Level.OFF);
    }

    @Override
    public void start(Stage primaryStage) {
        try {
            var appPreferences = new AppPreferences();
            var aiConfiguration = new AiConfiguration(appPreferences);
            var mainWindow = new MainWindow(aiConfiguration, appPreferences);

            SingleInstanceLock.setOnActivate(mainWindow::showMainWindow);

            mainWindow.showWindow();
            initLog.info("Main window displayed.");
        } catch (Exception e) {
            initLog.error("Failed to show main window", e);
        }
    }

    private static void setupCookies() {
        var manager = new CookieManager();
        manager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        CookieHandler.setDefault(manager);
    }

    public static class GlobalExceptionHandler implements Thread.UncaughtExceptionHandler {

        @Override
        public void uncaughtException(Thread t, Throwable e) {
            if (isKnownJavaFxWebViewBug(e)) {
                return;
            }
            if (initLog != null) {
                initLog.error("Critical Uncaught Error in thread {}", t.getName(), e);
            } else {
                System.err.println("Critical Uncaught Error:");
                e.printStackTrace();
            }
        }

        /**
         * JavaFX 21 WebView has several unfixed internal bugs that produce
         * exception spam without actually crashing the app. Suppress them.
         * <p>
         * 1. NullPointerException from HTTP2Loader — Perplexity and other sites
         * trigger an internal NPE in WebKit's HTTP/2 client. The top frame is
         * Objects.requireNonNull, so we must scan all frames for webkit classes.
         * <p>
         * 2. OutOfMemoryError from font engine — Apple Color Emoji SBIX tables
         * (~150-200 MB) overflow a small heap. Fixed by -Xmx384m in build.gradle,
         * but log a warning in case it still appears.
         */
        private static boolean isKnownJavaFxWebViewBug(Throwable e) {
            if (e instanceof NullPointerException && hasWebKitFrame(e)) {
                return true;
            }
            if (e instanceof OutOfMemoryError && hasWebKitOrFontFrame(e)) {
                if (initLog != null) {
                    initLog.warn("JavaFX WebKit OOM (Apple Color Emoji SBIX?) — increase -Xmx if it persists");
                }
                return true;
            }
            return false;
        }

        private static boolean hasWebKitFrame(Throwable e) {
            for (var frame : e.getStackTrace()) {
                var cls = frame.getClassName();
                if (cls.startsWith("com.sun.webkit") || cls.startsWith("com.sun.javafx.webkit")) {
                    return true;
                }
            }
            return false;
        }

        private static boolean hasWebKitOrFontFrame(Throwable e) {
            for (var frame : e.getStackTrace()) {
                var cls = frame.getClassName();
                if (cls.startsWith("com.sun.webkit")
                    || cls.startsWith("com.sun.javafx.webkit")
                    || cls.startsWith("com.sun.javafx.font")) {
                    return true;
                }
            }
            return false;
        }
    }
}
