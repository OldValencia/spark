package to.sparkapp.app;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.util.Duration;
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
    /** The first site starts loading about half a second after the window appears on a fresh profile. */
    private static final Duration AOT_TRAINING_TIME_AFTER_WINDOW = Duration.seconds(1);

    private static Logger initLog;

    public static void main(String[] args) {
        LogSetup.init();
        initLog = LoggerFactory.getLogger(SparkApplication.class);

        if (!SingleInstanceLock.tryAcquire()) {
            initLog.info("Another instance is already running — exiting");
            System.exit(0);
            return;
        }

        Runtime.getRuntime().addShutdownHook(new Thread(SingleInstanceLock::release));
        Thread.setDefaultUncaughtExceptionHandler(new GlobalExceptionHandler());
        setupCookies();
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        MainWindow mainWindow = null;

        try {
            var appPreferences = new AppPreferences();
            var aiConfiguration = new AiConfiguration(appPreferences);
            mainWindow = new MainWindow(aiConfiguration, appPreferences);

            SingleInstanceLock.setOnActivate(mainWindow::showMainWindow);

            mainWindow.showWindow();
            initLog.info("Main window displayed.");
        } catch (Exception e) {
            initLog.error("Failed to show main window", e);
        }

        if (Boolean.getBoolean("spark.aot.training")) {
            finishAotTraining(mainWindow);
        }
    }

    /**
     * The packaging-time training run records the classes Spark loads until its window is shown and the first
     * site starts loading in the browser, then quits like the tray menu does. Exiting while the browser runs
     * would skip its shutdown.
     */
    private static void finishAotTraining(MainWindow mainWindow) {
        var finish = new PauseTransition(AOT_TRAINING_TIME_AFTER_WINDOW);
        finish.setOnFinished(event -> {
            System.out.println("AOT training run finished. Exiting...");

            if (mainWindow != null) {
                mainWindow.quit();
            } else {
                Platform.exit();
                System.exit(0);
            }
        });
        finish.play();
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
