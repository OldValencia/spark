package io.loom.app.windows;

import io.loom.app.ui.settings.SettingsPanel;
import io.loom.app.utils.SystemUtils;
import lombok.RequiredArgsConstructor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

@RequiredArgsConstructor
public class SettingsWindow {

    private static final int TOPBAR_HEIGHT = 48;
    private static final float LERP_SPEED = 0.3f;

    private final JFrame owner;
    private final SettingsPanel settingsPanel;
    private JWindow window;

    private float progress = 0f;
    private float targetProgress = 0f;
    private Timer animTimer;

    private int targetHeight = 0;

    public void open() {
        if (window == null) {
            createWindow();
        }

        settingsPanel.setSize(owner.getWidth(), Integer.MAX_VALUE);
        settingsPanel.doLayout();

        int contentHeight = settingsPanel.getPreferredSize().height;
        int maxHeight = owner.getHeight() - TOPBAR_HEIGHT;
        this.targetHeight = Math.min(contentHeight, maxHeight);

        int x = owner.getX();
        int y = owner.getY() + TOPBAR_HEIGHT;
        int w = owner.getWidth();

        if (SystemUtils.isMac()) {
            if (animTimer.isRunning()) animTimer.stop();

            window.setBounds(x, y, w, targetHeight);
            settingsPanel.setOpaque(true);
            window.setVisible(true);

            SwingUtilities.invokeLater(() -> {
                window.toFront();
                window.repaint();
            });

            progress = 1f;
            targetProgress = 1f;
        } else {
            if (!window.isVisible()) {
                window.setBounds(x, y, w, 0);
                window.setVisible(true);
            } else {
                window.setBounds(x, y, w, window.getHeight());
            }
            window.toFront();

            targetProgress = 1f;
            animTimer.start();
        }
    }

    public void close() {
        if (window == null) {
            return;
        }

        if (SystemUtils.isMac()) {
            if (animTimer.isRunning()) animTimer.stop();
            window.setVisible(false);
            progress = 0f;
            targetProgress = 0f;
        } else {
            targetProgress = 0f;
            animTimer.start();
        }
    }

    public boolean isOpen() {
        return window != null && targetProgress > 0.5f;
    }

    public boolean isVisible() {
        return window != null && window.isVisible();
    }

    private void createWindow() {
        window = new JWindow(owner);
        window.setAlwaysOnTop(true);
        window.setFocusableWindowState(true);
        window.setBackground(new Color(0, 0, 0, 0));

        owner.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentHidden(ComponentEvent e) {
                if (window != null) {
                    window.setVisible(false);
                }
                progress = 0f;
                targetProgress = 0f;
                if (animTimer != null && animTimer.isRunning()) {
                    animTimer.stop();
                }
            }
        });

        var scrollPane = new JScrollPane(settingsPanel);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(null);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(10);
        scrollPane.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));

        window.setContentPane(scrollPane);

        animTimer = new Timer(10, e -> tick());
    }

    private void tick() {
        float diff = targetProgress - progress;

        if (Math.abs(diff) < 0.01f) {
            progress = targetProgress;
            animTimer.stop();
            if (progress <= 0f) {
                window.setVisible(false);
            }
        } else {
            progress += diff * LERP_SPEED;
        }

        int currentH = Math.round(targetHeight * progress);

        if (window.isVisible()) {
            window.setSize(window.getWidth(), currentH);
        }

        settingsPanel.setOpaque(progress > 0.8f);
    }
}
