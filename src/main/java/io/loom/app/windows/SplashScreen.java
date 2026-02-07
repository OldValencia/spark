package io.loom.app.windows;

import io.loom.app.ui.Theme;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

public class SplashScreen extends JWindow {

    private static final int WIDTH = 400;
    private static final int HEIGHT = 250;
    private static final int RADIUS = 20;

    private final JLabel statusLabel;

    public SplashScreen() {
        setSize(WIDTH, HEIGHT);
        setLocationRelativeTo(null);
        setAlwaysOnTop(true);

        var contentPane = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                var g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Background
                g2.setColor(Theme.BG_BAR);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), RADIUS, RADIUS));

                // Border
                g2.setColor(Theme.BORDER);
                g2.setStroke(new BasicStroke(1f));
                g2.draw(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, RADIUS, RADIUS));
            }
        };
        contentPane.setLayout(new BorderLayout());
        contentPane.setOpaque(false);
        contentPane.setBorder(BorderFactory.createEmptyBorder(30, 40, 30, 40));

        // Logo/Icon area
        var logoPanel = createLogoPanel();
        contentPane.add(logoPanel, BorderLayout.NORTH);

        // Title
        JLabel titleLabel = new JLabel("Loom", SwingConstants.CENTER);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 32));
        titleLabel.setForeground(Theme.TEXT_PRIMARY);
        titleLabel.setBorder(BorderFactory.createEmptyBorder(10, 0, 20, 0));
        contentPane.add(titleLabel, BorderLayout.CENTER);

        // Progress area
        var progressPanel = new JPanel(new BorderLayout(0, 10));
        progressPanel.setOpaque(false);

        statusLabel = new JLabel("Initializing browser engine...", SwingConstants.CENTER);
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        statusLabel.setForeground(Theme.TEXT_SECONDARY);
        progressPanel.add(statusLabel, BorderLayout.NORTH);

        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setPreferredSize(new Dimension(WIDTH - 80, 6));
        progressBar.setBorderPainted(false);
        progressBar.setForeground(Theme.ACCENT);
        progressBar.setBackground(Theme.BG_DEEP);
        progressPanel.add(progressBar, BorderLayout.CENTER);

        contentPane.add(progressPanel, BorderLayout.SOUTH);

        setContentPane(contentPane);
        setShape(new RoundRectangle2D.Double(0, 0, WIDTH, HEIGHT, RADIUS, RADIUS));
    }

    private JPanel createLogoPanel() {
        var panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setPreferredSize(new Dimension(WIDTH - 80, 60));

        var iconUrl = getClass().getResource("/app-icons/icon.png");
        if (iconUrl != null) {
            try {
                var icon = new ImageIcon(iconUrl);
                var scaledIcon = new ImageIcon(icon.getImage().getScaledInstance(48, 48, Image.SCALE_SMOOTH));
                var iconLabel = new JLabel(scaledIcon);
                iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
                panel.add(iconLabel, BorderLayout.CENTER);
            } catch (Exception e) {
                addFallbackLogo(panel);
            }
        } else {
            addFallbackLogo(panel);
        }

        return panel;
    }

    private void addFallbackLogo(JPanel panel) {
        var logoLabel = new JLabel("L", SwingConstants.CENTER);
        logoLabel.setFont(new Font("SansSerif", Font.BOLD, 48));
        logoLabel.setForeground(Theme.ACCENT);
        panel.add(logoLabel, BorderLayout.CENTER);
    }

    public void updateStatus(String status) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(status));
    }

    public void showSplash() {
        SwingUtilities.invokeLater(() -> setVisible(true));
    }

    public void hideSplash() {
        SwingUtilities.invokeLater(() -> {
            setVisible(false);
            dispose();
        });
    }
}
