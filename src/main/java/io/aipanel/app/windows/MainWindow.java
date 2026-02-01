package io.aipanel.app.windows;

import io.aipanel.app.config.AiConfiguration;
import io.aipanel.app.config.AppPreferences;
import io.aipanel.app.ui.CefWebView;
import io.aipanel.app.ui.Theme;
import io.aipanel.app.ui.topbar.TopBarArea;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.RoundRectangle2D;

@Controller
@RequiredArgsConstructor
public class MainWindow {

    private final AiConfiguration aiConfiguration;
    private final AppPreferences appPreferences;

    private CefWebView cefWebView;

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

        cefWebView = getCefWebView();
        root.add(cefWebView, BorderLayout.CENTER);

        var frame = buildMainFrame();

        var topBarArea = new TopBarArea(aiConfiguration, cefWebView, frame, appPreferences);
        root.add(topBarArea.createTopBar(), BorderLayout.NORTH);

        frame.add(root);
        frame.setVisible(true);
    }

    private CefWebView getCefWebView() {
        String startUrl = null;

        if (appPreferences.isRememberLastAi()) {
            startUrl = appPreferences.getLastUrl();
        }

        if (startUrl == null && !aiConfiguration.getConfigurations().isEmpty()) {
            startUrl = aiConfiguration.getConfigurations().getFirst().url();
        }

        if (startUrl == null) {
            startUrl = "https://chatgpt.com";
        }

        return new CefWebView(startUrl);
    }

    private JFrame buildMainFrame() {
        var frame = new JFrame("AI Panel");
        frame.setUndecorated(true);
        frame.setAlwaysOnTop(true);
        frame.setSize(WIDTH, HEIGHT);
        frame.setLocationRelativeTo(null);
        frame.setBackground(new Color(0, 0, 0, 0));
        frame.setShape(new RoundRectangle2D.Double(0, 0, WIDTH, HEIGHT, RADIUS, RADIUS));
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cefWebView.shutdown(() -> System.exit(0));
            }
        });
        return frame;
    }
}