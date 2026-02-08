package io.loom.app.ui.dialogs;

import io.loom.app.config.AiConfiguration;
import io.loom.app.ui.dialogs.components.ProviderFormPanel;
import io.loom.app.ui.dialogs.components.ProviderMainPanel;
import io.loom.app.ui.dialogs.components.ProviderTitleLabel;
import io.loom.app.ui.dialogs.components.ProvidersFormButtonsPanel;
import io.loom.app.utils.SystemUtils;
import lombok.Getter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.geom.RoundRectangle2D;

public class ProviderEditDialog extends JDialog {

    private final ProviderFormPanel providerFormPanel;

    @Getter
    private boolean confirmed = false;

    public ProviderEditDialog(Frame owner, AiConfiguration.AiConfig provider) {
        super(owner, provider == null ? "Add AI Provider" : "Edit AI Provider", true);
        this.setUndecorated(true);
        this.setBackground(new Color(0, 0, 0, 0));
        this.setLayout(new BorderLayout());

        var mainPanel = new ProviderMainPanel();
        mainPanel.add(new ProviderTitleLabel(provider == null), BorderLayout.NORTH);

        providerFormPanel = new ProviderFormPanel(provider);
        mainPanel.add(providerFormPanel, BorderLayout.CENTER);
        mainPanel.add(buildButtonsPanel(provider), BorderLayout.SOUTH);

        this.add(mainPanel);

        this.pack();

        int width = 420;
        int height = SystemUtils.isWindows() ? 330 : 290;

        this.setSize(width, height);
        this.setLocationRelativeTo(owner);
        this.setShape(new RoundRectangle2D.Double(0, 0, width, height, 14, 14));
    }

    private ProvidersFormButtonsPanel buildButtonsPanel(AiConfiguration.AiConfig provider) {
        ActionListener actionConfirmed = e -> {
            if (validateForm()) {
                confirmed = true;
                dispose();
            }
        };
        ActionListener actionCancelled = e -> {
            confirmed = false;
            dispose();
        };
        return new ProvidersFormButtonsPanel(provider == null, actionConfirmed, actionCancelled);
    }

    private boolean validateForm() {
        var name = getProviderName();
        var url = getProviderUrl();

        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a provider name", "Validation Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        if (!url.startsWith("http")) {
            JOptionPane.showMessageDialog(this, "Please enter a valid URL (starting with http:// or https://)", "Validation Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        return true;
    }

    public String getProviderName() {
        return providerFormPanel.getNameFieldValue();
    }

    public String getProviderUrl() {
        return providerFormPanel.getUrlFieldValue();
    }
}
