package io.loom.app.ui.settings.components;

import io.loom.app.config.AiConfiguration;
import io.loom.app.ui.Theme;
import io.loom.app.utils.SystemUtils;

import javax.swing.*;
import java.awt.*;

class ProviderListItem extends JPanel {

    ProviderListItem(AiConfiguration.AiConfig provider, Runnable onEdit, Runnable onDelete) {
        this.setOpaque(false);
        this.setLayout(new BorderLayout(12, 0));
        this.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));

        int itemHeight = SystemUtils.isWindows() ? 40 : 50;
        this.setMaximumSize(new Dimension(Integer.MAX_VALUE, itemHeight));

        this.add(createColorStrip(provider.color()), BorderLayout.WEST);
        this.add(createInfoPanel(provider), BorderLayout.CENTER);
        this.add(createActionButtons(onEdit, onDelete), BorderLayout.EAST);
    }

    private JComponent createColorStrip(String colorHex) {
        var panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                var g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                try {
                    g2.setColor(Color.decode(colorHex));
                } catch (Exception e) {
                    g2.setColor(Theme.ACCENT);
                }
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 4, 4);
            }
        };
        panel.setOpaque(false);
        panel.setPreferredSize(new Dimension(4, 32));
        return panel;
    }

    private JPanel createInfoPanel(AiConfiguration.AiConfig provider) {
        var infoPanel = new JPanel();
        infoPanel.setOpaque(false);

        if (SystemUtils.isWindows()) {
            infoPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));

            var nameLabel = new JLabel(provider.name());
            nameLabel.setFont(Theme.FONT_SETTINGS.deriveFont(Font.BOLD));
            nameLabel.setForeground(Theme.TEXT_PRIMARY);

            var urlLabel = new JLabel(" (" + provider.url() + ")");
            urlLabel.setFont(Theme.FONT_SETTINGS.deriveFont(11f));
            urlLabel.setForeground(Theme.TEXT_TERTIARY);

            infoPanel.add(nameLabel);
            infoPanel.add(urlLabel);
        } else {
            infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));

            var nameLabel = new JLabel(provider.name());
            nameLabel.setFont(Theme.FONT_SETTINGS.deriveFont(Font.BOLD));
            nameLabel.setForeground(Theme.TEXT_PRIMARY);
            nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

            var urlLabel = new JLabel(provider.url());
            urlLabel.setFont(Theme.FONT_SETTINGS.deriveFont(11f));
            urlLabel.setForeground(Theme.TEXT_TERTIARY);
            urlLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

            infoPanel.add(nameLabel);
            infoPanel.add(Box.createVerticalStrut(2));
            infoPanel.add(urlLabel);
        }

        return infoPanel;
    }

    private JPanel createActionButtons(Runnable onEdit, Runnable onDelete) {
        var actionsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actionsPanel.setOpaque(false);

        actionsPanel.add(new ProvidersListTextButton("Edit", Theme.TEXT_SECONDARY, e -> onEdit.run()));
        actionsPanel.add(new ProvidersListTextButton("Delete", Theme.TEXT_SECONDARY, e -> onDelete.run()));

        return actionsPanel;
    }
}
