package io.aipanel.app.windows;

import io.aipanel.app.config.AiConfiguration;
import io.aipanel.app.config.AppPreferences; // <-- Импортируем наш новый класс
import io.aipanel.app.ui.CefWebView;
import io.aipanel.app.ui.Theme;
import io.aipanel.app.ui.topbar.TopBarArea;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

@Controller
@RequiredArgsConstructor
public class MainWindow {

    private final AiConfiguration aiConfiguration;
    // Создаем экземпляр настроек. В идеале сделать Bean,
    // но пока можно просто создать поле final, так как это легковесный класс.
    private final AppPreferences appPreferences = new AppPreferences();

    private static final int WIDTH = 820;
    private static final int HEIGHT = 700;
    private static final int RADIUS = 14;

    public void showWindow() {
        var root = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                var g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.BG_DEEP);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), RADIUS, RADIUS));
            }
        };
        root.setBackground(Theme.BG_DEEP);

        // === ЛОГИКА ВЫБОРА URL ПРИ СТАРТЕ ===
        String startUrl = null;

        // 1. Пытаемся достать сохраненный
        if (appPreferences.isRememberLastAi()) {
            startUrl = appPreferences.getLastUrl();
        }

        // 2. Если сохраненного нет или он пустой - берем первый из конфига
        if (startUrl == null && !aiConfiguration.getConfigurations().isEmpty()) {
            startUrl = aiConfiguration.getConfigurations().getFirst().url();
        }

        // 3. Фолбэк, если вообще ничего нет
        if (startUrl == null) {
            startUrl = "https://chatgpt.com";
        }

        // Создаем WebView
        var cefWebView = new CefWebView(startUrl);
        root.add(cefWebView, BorderLayout.CENTER);

        var frame = buildMainFrame();

        // === ВАЖНО: Передаем appPreferences в TopBarArea (или AiDock) ===
        // Но сейчас TopBarArea принимает только config и webView.
        // Чтобы AiDock мог сохранять выбор, нам нужно передать ему preferences.
        // Давай сделаем это аккуратно через модификацию AiDock.

        // Передаем appPreferences в TopBarArea
        var topBarArea = new TopBarArea(aiConfiguration, cefWebView, frame, appPreferences);
        root.add(topBarArea.createTopBar(), BorderLayout.NORTH);

        frame.add(root);
        frame.setVisible(true);
    }

    private JFrame buildMainFrame() {
        var frame = new JFrame("AI Panel");
        frame.setUndecorated(true);
        frame.setAlwaysOnTop(true);
        frame.setSize(WIDTH, HEIGHT);
        frame.setLocationRelativeTo(null);
        frame.setBackground(new Color(0, 0, 0, 0));
        frame.setShape(new RoundRectangle2D.Double(0, 0, WIDTH, HEIGHT, RADIUS, RADIUS));
        return frame;
    }
}