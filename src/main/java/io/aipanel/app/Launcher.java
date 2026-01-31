package io.aipanel.app;

import javafx.application.Application;

public class Launcher {
    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "false");
        Application.launch(AIPanelApplication.class, args);
    }
}
