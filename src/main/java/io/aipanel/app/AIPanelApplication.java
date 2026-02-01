package io.aipanel.app;

import io.aipanel.app.config.AiConfiguration;
import io.aipanel.app.config.AppPreferences;
import io.aipanel.app.utils.LogSetup;
import io.aipanel.app.windows.MainWindow;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;

@Slf4j
public class AIPanelApplication {

    public static void main(String[] args) {
        LogSetup.init();

        System.setProperty("sun.awt.exception.handler", AwtExceptionHandler.class.getName());

        setupCookies();

        var aiConfiguration = new AiConfiguration();
        var appPreferences = new AppPreferences();

        SwingUtilities.invokeLater(() -> {
            try {
                var mainWindow = new MainWindow(aiConfiguration, appPreferences);
                mainWindow.showWindow();
                log.info("Main window displayed.");
            } catch (Exception e) {
                log.error("Failed to show main window", e);
            }
        });
    }

    private static void setupCookies() {
        var manager = new CookieManager();
        manager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        CookieHandler.setDefault(manager);
    }

    public static class AwtExceptionHandler {
        public void handle(Throwable t) {
            log.error("Critical AWT Error", t);
        }
    }
}