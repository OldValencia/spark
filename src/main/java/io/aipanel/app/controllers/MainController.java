package io.aipanel.app.controllers;

import io.aipanel.app.config.AiConfiguration;
import io.aipanel.app.ui.CefWebView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

@Controller
@RequiredArgsConstructor
public class MainController {

    private final AiConfiguration aiConfiguration;
    private JFrame frame;
    private CefWebView cefWebView;
    private JComboBox<AiConfiguration.AiConfig> aiSelectBox;
    private Point initialClick;

    public void showWindow() {
        frame = new JFrame("AI Panel");
        frame.setUndecorated(true); // Для кастомного стиля как в CSS
        frame.setSize(1800, 1700);
        frame.setLocationRelativeTo(null);
        frame.setBackground(new Color(30, 30, 30));

        // Основная панель (аналог BorderPane)
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(new Color(30, 30, 30));
        root.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 60), 1));

        // Верхняя панель (аналог HBox)
        JPanel topBar = createTopBar();
        root.add(topBar, BorderLayout.NORTH);

        // Контейнер для браузера
        String startUrl = aiConfiguration.getConfigurations().isEmpty()
                ? "https://google.com"
                : aiConfiguration.getConfigurations().getFirst().url();

        cefWebView = new CefWebView(startUrl);
        cefWebView.setBorder(new EmptyBorder(0, 12, 12, 12));
        root.add(cefWebView, BorderLayout.CENTER);

        frame.add(root);
        setupDragging(topBar);
        frame.setVisible(true);
    }

    private JPanel createTopBar() {
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setPreferredSize(new Dimension(800, 50));
        topBar.setBackground(new Color(30, 30, 30));
        topBar.setBorder(new EmptyBorder(0, 20, 0, 20));

        // Левая часть: ComboBox
        aiSelectBox = new JComboBox<>(aiConfiguration.getConfigurations().toArray(new AiConfiguration.AiConfig[0]));
        aiSelectBox.setRenderer(new AiListCellRenderer());
        aiSelectBox.addActionListener(e -> {
            var selected = (AiConfiguration.AiConfig) aiSelectBox.getSelectedItem();
            if (selected != null) cefWebView.loadUrl(selected.url());
        });

        JPanel leftWrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 12));
        leftWrapper.setOpaque(false);
        leftWrapper.add(aiSelectBox);
        topBar.add(leftWrapper, BorderLayout.WEST);

        // Правая часть: Кнопки
        JPanel rightWrapper = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 12));
        rightWrapper.setOpaque(false);

        JButton closeBtn = new JButton("✕");
        styleButton(closeBtn, true);
        closeBtn.addActionListener(e -> handleClose());

        rightWrapper.add(closeBtn);
        topBar.add(rightWrapper, BorderLayout.EAST);

        return topBar;
    }

    private void styleButton(JButton btn, boolean isClose) {
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setForeground(Color.LIGHT_GRAY);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        if (isClose) {
            btn.addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { btn.setForeground(Color.RED); }
                public void mouseExited(MouseEvent e) { btn.setForeground(Color.LIGHT_GRAY); }
            });
        }
    }

    private void setupDragging(JPanel panel) {
        panel.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) { initialClick = e.getPoint(); }
        });
        panel.addMouseMotionListener(new MouseAdapter() {
            public void mouseDragged(MouseEvent e) {
                int thisX = frame.getLocation().x;
                int thisY = frame.getLocation().y;
                int xMoved = e.getX() - initialClick.x;
                int yMoved = e.getY() - initialClick.y;
                frame.setLocation(thisX + xMoved, thisY + yMoved);
            }
        });
    }

    private void handleClose() {
        cefWebView.dispose();
        System.exit(0);
    }

    // Рендерер для иконок в выпадающем списке
    private static class AiListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof AiConfiguration.AiConfig config) {
                label.setText(config.name());
                if (config.icon() != null) {
                    var imgUrl = getClass().getResource("/icons/" + config.icon());
                    if (imgUrl != null) {
                        ImageIcon icon = new ImageIcon(imgUrl);
                        Image scaled = icon.getImage().getScaledInstance(18, 18, Image.SCALE_SMOOTH);
                        label.setIcon(new ImageIcon(scaled));
                    }
                }
                label.setBorder(new EmptyBorder(5, 5, 5, 5));
            }
            return label;
        }
    }
}