package io.loom.app.windows;

import io.loom.app.ui.settings.SettingsPanel;
import lombok.RequiredArgsConstructor;

import javax.swing.*;
import java.awt.*;

@RequiredArgsConstructor
public class SettingsWindow {

    private static final int TOPBAR_HEIGHT = 48;
    private static final float LERP_SPEED = 0.22f;

    private final JFrame owner;
    private final SettingsPanel settingsPanel;
    private JWindow window;

    private float progress = 0f;
    private float targetProgress = 0f;
    private Timer animTimer;
    private int currentTargetHeight = 0;

    public void open() {
        if (window == null) {
            createWindow();
        }
        settingsPanel.setSize(owner.getWidth(), Integer.MAX_VALUE);
        settingsPanel.doLayout();
        currentTargetHeight = settingsPanel.getPreferredSize().height;

        targetProgress = 1f;
        window.setVisible(true);
        animTimer.start();
    }

    public void close() {
        if (window == null) {
            return;
        }
        targetProgress = 0f;
        animTimer.start();
    }

    public boolean isOpen() {
        return targetProgress > 0.5f;
    }

    public void dragWindow(int dx, int dy) {
        var frameLocation = window.getLocation();
        window.setLocation(frameLocation.x + dx, frameLocation.y + dy);
    }

    private void createWindow() {
        window = new JWindow(owner);
        window.setAlwaysOnTop(true);
        window.setBackground(new Color(0, 0, 0, 0));
        window.setContentPane(settingsPanel);

        animTimer = new Timer(16, e -> tick());
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

        applyBounds();
    }

    private void applyBounds() {
        if (window == null) {
            return;
        }

        int x = owner.getX();
        int y = owner.getY() + TOPBAR_HEIGHT;
        int w = owner.getWidth();
        int h = (int) (currentTargetHeight * progress);

        window.setBounds(x, y, w, Math.max(h, 1));
    }
}
