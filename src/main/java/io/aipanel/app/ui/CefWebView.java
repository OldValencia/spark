package io.aipanel.app.ui;

import me.friwi.jcefmaven.CefAppBuilder;
import me.friwi.jcefmaven.CefInitializationException;
import me.friwi.jcefmaven.UnsupportedPlatformException;
import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;

public class CefWebView extends JPanel {

    private CefApp app;
    private CefClient client;
    private CefBrowser browser;
    private final String logDir = System.getProperty("user.home") + File.separator + ".aipanel";

    public CefWebView(String startUrl) {
        setLayout(new BorderLayout());
        setBackground(new Color(30, 30, 30));

        // Запускаем инициализацию CEF
        initCef(startUrl);
    }

    private void initCef(String startUrl) {
        try {
            CefAppBuilder builder = new CefAppBuilder();
            builder.setInstallDir(new File(logDir, "jcef-bundle"));

            // Для Swing windowless_rendering_enabled можно выключить для лучшей производительности,
            // если не нужны прозрачные наложения поверх браузера
            builder.getCefSettings().windowless_rendering_enabled = false;

            app = builder.build();
            client = app.createClient();
            browser = client.createBrowser(startUrl, false, false);

            // Просто добавляем UI компонент браузера в нашу панель
            add(browser.getUIComponent(), BorderLayout.CENTER);

            revalidate();
        } catch (IOException | UnsupportedPlatformException | InterruptedException | CefInitializationException e) {
            e.printStackTrace();
        }
    }

    public void loadUrl(String url) {
        if (browser != null) browser.loadURL(url);
    }

    public void dispose() {
        if (browser != null) browser.close(true);
        if (client != null) client.dispose();
    }
}