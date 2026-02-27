package to.sparkapp.app;

import javafx.application.Platform;
import to.sparkapp.app.config.AiConfiguration;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.utils.LogSetup;
import to.sparkapp.app.utils.SingleInstanceLock;
import to.sparkapp.app.windows.MainWindow;
import javafx.application.Application;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;

@Slf4j
public class SparkApplication extends Application {

    private static Logger initLog;

    public static void main(String[] args) {
        if (Boolean.getBoolean("spark.cds.generate")) {
            System.exit(0);
        }

        LogSetup.init();
        initLog = LoggerFactory.getLogger(SparkApplication.class);

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

    @Override
    public void start(Stage primaryStage) {
        if (Boolean.getBoolean("spark.cds.generate")) {
            System.out.println("CDS Archive successfully generated. Exiting...");
            Platform.exit();
            System.exit(0);
        }

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
            if (initLog != null) {
                initLog.error("Critical Uncaught Error in thread {}", t.getName(), e);
            } else {
                System.err.println("Critical Uncaught Error:");
                e.printStackTrace();
            }
        }
    }
}
