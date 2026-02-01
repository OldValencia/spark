package io.aipanel.app.windows;

import io.aipanel.app.ui.settings.SettingsPanel;
import lombok.RequiredArgsConstructor;

import javax.swing.*;
import java.awt.*;

@RequiredArgsConstructor
public class SettingsWindow {

    private static final int TOPBAR_H = 48;
    private static final int PANEL_HEIGHT = 120;
    private static final float LERP_SPEED = 0.22f;

    private final JFrame owner;
    private final SettingsPanel settingsPanel;
    private JWindow window;

    private float progress = 0f;
    private float targetProgress = 0f;
    private Timer animTimer;

    public void open() {
        if (window == null) {
            createWindow();
        }
        targetProgress = 1f;
        window.setVisible(true);
        animTimer.start();
    }

    public void close() {
        if (window == null) return;
        targetProgress = 0f;
        animTimer.start();
    }

    public boolean isOpen() {
        return targetProgress > 0.5f;
    }

    public void setPosition(int dx, int dy) {
        if (window == null) {
            return;
        }

        int x = owner.getX() + dx;
        int y = owner.getY() + TOPBAR_H + dy;
        int w = owner.getWidth();
        int h = (int) (PANEL_HEIGHT * progress);

        window.setBounds(x, y, w, Math.max(h, 1));
    }

    private void createWindow() {
        window = new JWindow(owner);
        window.setAlwaysOnTop(true);
        window.setBackground(new Color(0, 0, 0, 0));
        window.setContentPane(settingsPanel);

        animTimer = new Timer(16, e -> tick());
    }

    private void tick() {
        var diff = targetProgress - progress;

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
        if (window == null) return;

        int x = owner.getX();
        int y = owner.getY() + TOPBAR_H;
        int w = owner.getWidth();
        int h = (int) (PANEL_HEIGHT * progress);

        window.setBounds(x, y, w, Math.max(h, 1));
    }
}
