package io.loom.app.ui.dialogs;

import io.loom.app.config.CustomAiProvidersManager.CustomProvider;
import io.loom.app.ui.Theme;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Диалог для добавления или редактирования AI провайдера
 */
public class ProviderEditDialog extends JDialog {

    private final JTextField nameField;
    private final JTextField urlField;
    private boolean confirmed = false;

    public ProviderEditDialog(Frame owner, CustomProvider provider) {
        super(owner, provider == null ? "Add AI Provider" : "Edit AI Provider", true);

        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0));
        setLayout(new BorderLayout());

        var mainPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                var g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.BG_BAR);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 14, 14));
            }
        };
        mainPanel.setOpaque(false);
        mainPanel.setLayout(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        var titleLabel = new JLabel(provider == null ? "Add New AI Provider" : "Edit AI Provider");
        titleLabel.setFont(Theme.FONT_SETTINGS.deriveFont(Font.BOLD, 18f));
        titleLabel.setForeground(Theme.TEXT_PRIMARY);
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 16, 0));
        mainPanel.add(titleLabel, BorderLayout.NORTH);

        var formPanel = new JPanel();
        formPanel.setOpaque(false);
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));

        formPanel.add(createLabel("Provider Name:"));
        nameField = createTextField(provider != null ? provider.getName() : "");
        formPanel.add(nameField);
        formPanel.add(Box.createVerticalStrut(12));

        formPanel.add(createLabel("Website URL:"));
        urlField = createTextField(provider != null ? provider.getUrl() : "https://");
        formPanel.add(urlField);
        formPanel.add(Box.createVerticalStrut(8));

        var hintLabel = new JLabel("<html><i>Icon and color will be automatically extracted from the website</i></html>");
        hintLabel.setFont(Theme.FONT_SETTINGS.deriveFont(11f));
        hintLabel.setForeground(Theme.TEXT_TERTIARY);
        hintLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        formPanel.add(hintLabel);

        mainPanel.add(formPanel, BorderLayout.CENTER);

        var buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonsPanel.setOpaque(false);
        buttonsPanel.setBorder(BorderFactory.createEmptyBorder(16, 0, 0, 0));

        var cancelBtn = createButton("Cancel", false);
        cancelBtn.addActionListener(e -> {
            confirmed = false;
            dispose();
        });

        var saveBtn = createButton(provider == null ? "Add" : "Save", true);
        saveBtn.addActionListener(e -> {
            if (validateForm()) {
                confirmed = true;
                dispose();
            }
        });

        buttonsPanel.add(cancelBtn);
        buttonsPanel.add(saveBtn);
        mainPanel.add(buttonsPanel, BorderLayout.SOUTH);

        add(mainPanel);

        pack();
        setSize(420, 290);
        setLocationRelativeTo(owner);
        setShape(new RoundRectangle2D.Double(0, 0, 420, 280, 14, 14));
    }

    private JLabel createLabel(String text) {
        var label = new JLabel(text);
        label.setFont(Theme.FONT_SETTINGS);
        label.setForeground(Theme.TEXT_SECONDARY);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        return label;
    }

    private JTextField createTextField(String text) {
        var field = new JTextField(text);
        field.setFont(Theme.FONT_SETTINGS);
        field.setForeground(Theme.TEXT_PRIMARY);
        field.setBackground(Theme.BG_POPUP);
        field.setCaretColor(Theme.TEXT_PRIMARY);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        return field;
    }

    private JButton createButton(String text, boolean primary) {
        var button = new JButton(text);
        button.setFont(Theme.FONT_SETTINGS);
        button.setForeground(primary ? Color.WHITE : Theme.TEXT_PRIMARY);
        button.setBackground(primary ? Theme.ACCENT : Theme.BG_POPUP);
        button.setBorder(BorderFactory.createEmptyBorder(10, 24, 10, 24));
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Hover effect
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                button.setBackground(primary ? Theme.ACCENT.brighter() : Theme.BG_HOVER);
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                button.setBackground(primary ? Theme.ACCENT : Theme.BG_POPUP);
            }
        });

        return button;
    }

    private boolean validateForm() {
        var name = nameField.getText().trim();
        var url = urlField.getText().trim();

        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a provider name", "Validation Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        if (url.isEmpty() || !url.startsWith("http")) {
            JOptionPane.showMessageDialog(this, "Please enter a valid URL (starting with http:// or https://)", "Validation Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        return true;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public String getProviderName() {
        return nameField.getText().trim();
    }

    public String getProviderUrl() {
        return urlField.getText().trim();
    }
}