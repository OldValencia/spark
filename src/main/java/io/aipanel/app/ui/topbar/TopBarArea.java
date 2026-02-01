package io.aipanel.app.ui.topbar;

import io.aipanel.app.config.AiConfiguration;
import io.aipanel.app.config.AppPreferences;
import io.aipanel.app.ui.CefWebView;
import io.aipanel.app.ui.topbar.components.GradientPanel;
import io.aipanel.app.windows.SettingsWindow;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

@RequiredArgsConstructor
public class TopBarArea {

    private final AiConfiguration aiConfiguration;
    private final CefWebView cefWebView;
    private final JFrame frame;
    private final SettingsWindow settingsWindow;
    private final AppPreferences appPreferences;
    private final Runnable onSettingsToggle;

    private Point initialClick;

    @Getter
    private GradientPanel topBarPanel;

    public JPanel createTopBar() {
        topBarPanel = new GradientPanel();
        topBarPanel.setPreferredSize(new Dimension(frame.getWidth(), 48));
        topBarPanel.setLayout(new BorderLayout());

        var leftTopBarArea = new LeftTopBarArea(aiConfiguration, cefWebView, appPreferences);
        topBarPanel.add(leftTopBarArea.buildLeftArea(), BorderLayout.WEST);

        var rightTopBarArea = new RightTopBarArea(cefWebView, onSettingsToggle);
        topBarPanel.add(rightTopBarArea.buildRightArea(), BorderLayout.EAST);

        setupDragging(topBarPanel);

        if (!aiConfiguration.getConfigurations().isEmpty()) {
            topBarPanel.updateAccentColor(Color.decode(aiConfiguration.getConfigurations().getFirst().color()));
        }

        return topBarPanel;
    }

    private void setupDragging(JPanel panel) {
        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                initialClick = e.getPoint();
            }
        });
        panel.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                int dx = e.getX() - initialClick.x;
                int dy = e.getY() - initialClick.y;
                var frameLocation = frame.getLocation();
                frame.setLocation(frameLocation.x + dx, frameLocation.y + dy);

                if (settingsWindow.isOpen()) {
                    settingsWindow.setPosition(dx, dy);
                }
            }
        });
    }
}
