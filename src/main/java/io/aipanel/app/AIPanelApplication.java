package io.aipanel.app;

import io.aipanel.app.config.AppConfig;
import io.aipanel.app.controllers.MainController;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import javax.swing.*;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;

public class AIPanelApplication {

    private static AnnotationConfigApplicationContext springContext;

    public static void main(String[] args) {
        System.setProperty("sun.awt.exception.handler", AwtExceptionHandler.class.getName());

        springContext = new AnnotationConfigApplicationContext();
        springContext.register(AppConfig.class);  // твоя конфигурация
        springContext.scan("io.aipanel.app");     // пакет с компонентами (если есть @Component)
        springContext.refresh();

        var manager = new CookieManager();
        manager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        CookieHandler.setDefault(manager);

        SwingUtilities.invokeLater(() -> {
            MainController controller = springContext.getBean(MainController.class);
            controller.showWindow();
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