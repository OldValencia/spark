package io.loom.app.ui.settings.components;

import io.loom.app.config.CustomAiProvidersManager;
import io.loom.app.config.CustomAiProvidersManager.CustomProvider;
import io.loom.app.ui.Theme;
import io.loom.app.ui.dialogs.ProviderEditDialog;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

/**
 * Панель для управления кастомными AI провайдерами
 */
public class ProvidersManagementPanel extends JPanel {

    private final CustomAiProvidersManager providersManager;
    private final Consumer<Void> onProvidersChanged;
    private final JPanel providersList;

    public ProvidersManagementPanel(CustomAiProvidersManager providersManager, Consumer<Void> onProvidersChanged) {
        this.providersManager = providersManager;
        this.onProvidersChanged = onProvidersChanged;

        setOpaque(false);
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));

        // Заголовок с кнопкой добавления
        var headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);

        var titleLabel = new JLabel("MY AI PROVIDERS");
        titleLabel.setFont(Theme.FONT_SETTINGS_SECTION);
        titleLabel.setForeground(Theme.TEXT_TERTIARY);

        var addButton = createAddButton();

        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(addButton, BorderLayout.EAST);
        add(headerPanel, BorderLayout.NORTH);

        // Список провайдеров
        providersList = new JPanel();
        providersList.setOpaque(false);
        providersList.setLayout(new BoxLayout(providersList, BoxLayout.Y_AXIS));

        var scrollPane = new JScrollPane(providersList);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        scrollPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
        add(scrollPane, BorderLayout.CENTER);

        refreshProvidersList();
    }

    private JButton createAddButton() {
        var button = new JButton("+ Add");
        button.setFont(Theme.FONT_SETTINGS.deriveFont(12f));
        button.setForeground(Theme.ACCENT);
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        button.addActionListener(e -> showAddDialog());

        return button;
    }

    private void refreshProvidersList() {
        providersList.removeAll();

        var providers = providersManager.getAllProviders();

        if (providers.isEmpty()) {
            var emptyLabel = new JLabel("No custom providers yet. Click '+ Add' to create one.");
            emptyLabel.setFont(Theme.FONT_SETTINGS.deriveFont(12f));
            emptyLabel.setForeground(Theme.TEXT_TERTIARY);
            emptyLabel.setBorder(BorderFactory.createEmptyBorder(20, 0, 20, 0));
            providersList.add(emptyLabel);
        } else {
            for (var provider : providers) {
                providersList.add(createProviderItem(provider));
                providersList.add(Box.createVerticalStrut(4));
            }
        }

        providersList.revalidate();
        providersList.repaint();
    }

    private JPanel createProviderItem(CustomProvider provider) {
        var panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BorderLayout(12, 0));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));

        // Цветовой индикатор
        var colorPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                var g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                try {
                    g2.setColor(Color.decode(provider.getColor()));
                } catch (Exception e) {
                    g2.setColor(Theme.ACCENT);
                }
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 4, 4);
            }
        };
        colorPanel.setOpaque(false);
        colorPanel.setPreferredSize(new Dimension(4, 32));
        panel.add(colorPanel, BorderLayout.WEST);

        // Информация
        var infoPanel = new JPanel();
        infoPanel.setOpaque(false);
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));

        var nameLabel = new JLabel(provider.getName());
        nameLabel.setFont(Theme.FONT_SETTINGS.deriveFont(Font.BOLD));
        nameLabel.setForeground(Theme.TEXT_PRIMARY);
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        var urlLabel = new JLabel(provider.getUrl());
        urlLabel.setFont(Theme.FONT_SETTINGS.deriveFont(11f));
        urlLabel.setForeground(Theme.TEXT_TERTIARY);
        urlLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        infoPanel.add(nameLabel);
        infoPanel.add(Box.createVerticalStrut(2));
        infoPanel.add(urlLabel);
        panel.add(infoPanel, BorderLayout.CENTER);

        // Кнопки действий
        var actionsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        actionsPanel.setOpaque(false);

        var editBtn = createActionButton("✎", "Edit");
        editBtn.addActionListener(e -> showEditDialog(provider));

        var deleteBtn = createActionButton("✕", "Delete");
        deleteBtn.addActionListener(e -> showDeleteConfirmation(provider));

        actionsPanel.add(editBtn);
        actionsPanel.add(deleteBtn);
        panel.add(actionsPanel, BorderLayout.EAST);

        return panel;
    }

    private JButton createActionButton(String icon, String tooltip) {
        var button = new JButton(icon);
        button.setFont(new Font("SansSerif", Font.PLAIN, 16));
        button.setForeground(Theme.TEXT_SECONDARY);
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(new Dimension(28, 28));
        button.setToolTipText(tooltip);

        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setForeground(Theme.TEXT_PRIMARY);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setForeground(Theme.TEXT_SECONDARY);
            }
        });

        return button;
    }

    private void showAddDialog() {
        // Получаем MainWindow Frame через иерархию окон
        var ownerFrame = getOwnerFrame();
        var dialog = new ProviderEditDialog(ownerFrame, null);
        dialog.setVisible(true);

        if (dialog.isConfirmed()) {
            var name = dialog.getProviderName();
            var url = dialog.getProviderUrl();

            // Показываем прогресс
            showProgress("Adding provider...");

            SwingWorker<Void, Void> worker = new SwingWorker<>() {
                @Override
                protected Void doInBackground() {
                    providersManager.addProvider(name, url);
                    return null;
                }

                @Override
                protected void done() {
                    hideProgress();
                    refreshProvidersList();
                    onProvidersChanged.accept(null);
                }
            };
            worker.execute();
        }
    }

    private void showEditDialog(CustomProvider provider) {
        var ownerFrame = getOwnerFrame();
        var dialog = new ProviderEditDialog(ownerFrame, provider);
        dialog.setVisible(true);

        if (dialog.isConfirmed()) {
            var name = dialog.getProviderName();
            var url = dialog.getProviderUrl();

            showProgress("Updating provider...");

            SwingWorker<Void, Void> worker = new SwingWorker<>() {
                @Override
                protected Void doInBackground() {
                    providersManager.updateProvider(provider.getId(), name, url);
                    return null;
                }

                @Override
                protected void done() {
                    hideProgress();
                    refreshProvidersList();
                    onProvidersChanged.accept(null);
                }
            };
            worker.execute();
        }
    }

    private void showDeleteConfirmation(CustomProvider provider) {
        var ownerFrame = getOwnerFrame();

        int result = JOptionPane.showConfirmDialog(
                ownerFrame,
                "Are you sure you want to delete \"" + provider.getName() + "\"?",
                "Confirm Deletion",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (result == JOptionPane.YES_OPTION) {
            providersManager.deleteProvider(provider.getId());
            refreshProvidersList();
            onProvidersChanged.accept(null);
        }
    }

    private void showProgress(String message) {
        // TODO: Показать индикатор загрузки
    }

    private void hideProgress() {
        // TODO: Скрыть индикатор загрузки
    }

    /**
     * Получает MainWindow Frame через иерархию окон
     * SettingsPanel -> JScrollPane -> JWindow -> owner (JFrame)
     */
    private Frame getOwnerFrame() {
        // Поднимаемся по иерархии до Window
        Window window = SwingUtilities.getWindowAncestor(this);

        if (window instanceof JWindow jWindow) {
            // У JWindow есть owner, который является JFrame
            Window owner = jWindow.getOwner();
            if (owner instanceof Frame frame) {
                return frame;
            }
        }

        // Fallback: ищем любой Frame в иерархии
        if (window instanceof Frame frame) {
            return frame;
        }

        // Крайний случай: возвращаем null, Dialog создастся без owner
        return null;
    }
}