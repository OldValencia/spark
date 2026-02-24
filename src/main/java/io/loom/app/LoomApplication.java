package io.loom.app;

import io.loom.app.config.AiConfiguration;
import io.loom.app.config.AppPreferences;
import io.loom.app.utils.LogSetup;
import io.loom.app.windows.MainWindow;
import javafx.application.Application;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;

@Slf4j
public class LoomApplication extends Application {

    private static Logger initLog;

    public static void main(String[] args) {
        LogSetup.init();
        initLog = LoggerFactory.getLogger(LoomApplication.class);
        Thread.setDefaultUncaughtExceptionHandler(new GlobalExceptionHandler());
        setupCookies();
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        try {
            var appPreferences = new AppPreferences();
            var aiConfiguration = new AiConfiguration(appPreferences);
            var mainWindow = new MainWindow(aiConfiguration, appPreferences);
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
