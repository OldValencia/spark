package io.aipanel.app;

import io.aipanel.app.config.AppConfig;
import io.aipanel.app.utils.LogSetup;
import io.aipanel.app.windows.MainWindow;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import javax.swing.*;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;

public class AIPanelApplication {

    private static AnnotationConfigApplicationContext springContext;

    public static void main(String[] args) {
        LogSetup.init();

        System.setProperty("sun.awt.exception.handler", AwtExceptionHandler.class.getName());

        springContext = new AnnotationConfigApplicationContext();
        springContext.register(AppConfig.class);
        springContext.scan("io.aipanel.app");
        springContext.refresh();

        var manager = new CookieManager();
        manager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        CookieHandler.setDefault(manager);

        SwingUtilities.invokeLater(() -> {
            var mainWindow = springContext.getBean(MainWindow.class);
            mainWindow.showWindow();
        });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (springContext != null) springContext.close();
        }));
    }

    public static class AwtExceptionHandler {
        public void handle(Throwable t) {
            System.err.println("!!! Critical error !!!");
            t.printStackTrace();
        }
    }
}