package io.loom.app.ui.settings.components;

import io.loom.app.ui.Theme;
import lombok.Setter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Consumer;

public class AnimatedToggleSwitch extends JPanel {

    private static final int WIDTH = 48;
    private static final int HEIGHT = 28;
    private static final int THUMB_SIZE = 22;
    private static final float ANIM_SPEED = 0.25f;

    private boolean enabled;
    private float progress;
    private final Timer animTimer;
    @Setter
    private Consumer<Boolean> onChange;

    public AnimatedToggleSwitch(boolean initialState) {
        this.enabled = initialState;
        this.progress = initialState ? 1f : 0f;

        setOpaque(false);
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setMaximumSize(new Dimension(WIDTH, HEIGHT));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        animTimer = new Timer(10, e -> tick());

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                toggle();
            }
        });
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            this.enabled = enabled;
            animTimer.start();
        }
    }

    private void toggle() {
        enabled = !enabled;
        animTimer.start();
        if (onChange != null) {
            onChange.accept(enabled);
        }
    }

    private void tick() {
        float target = enabled ? 1f : 0f;
        float diff = target - progress;

        if (Math.abs(diff) < 0.01f) {
            progress = target;
            animTimer.stop();
            Toolkit.getDefaultToolkit().sync();
        } else {
            progress += diff * ANIM_SPEED;
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        var g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        var bgColor = Theme.lerp(Theme.TOGGLE_BG_OFF, Theme.TOGGLE_BG_ON, progress);
        g.setColor(bgColor);
        g.fill(new RoundRectangle2D.Float(0, 0, WIDTH, HEIGHT, HEIGHT, HEIGHT));

        int thumbX = (int) ((WIDTH - THUMB_SIZE - 4) * progress) + 2;
        int thumbY = (HEIGHT - THUMB_SIZE) / 2;

        g.setColor(Theme.TOGGLE_THUMB);
        g.fill(new RoundRectangle2D.Float(thumbX, thumbY, THUMB_SIZE, THUMB_SIZE, THUMB_SIZE, THUMB_SIZE));
    }
}
