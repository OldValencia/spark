package io.aipanel.app.ui.topbar;

import io.aipanel.app.ui.CefWebView;
import io.aipanel.app.ui.Theme;
import io.aipanel.app.ui.topbar.components.AnimatedIconButton;
import io.aipanel.app.ui.topbar.components.ZoomButton;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import javax.swing.*;
import java.awt.*;

@RequiredArgsConstructor
class RightTopBarArea {

    private final CefWebView cefWebView;

    Box buildRightArea() {
        var box = Box.createHorizontalBox();

        var wrapper = new JPanel(new GridBagLayout());
        wrapper.setOpaque(false);

        var gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.CENTER;

        // Zoom
        var zoomButton = new ZoomButton(cefWebView::resetZoom);
        cefWebView.setZoomCallback(zoomButton::updateZoomDisplay);
        gbc.insets = new Insets(0, 0, 0, 15);
        wrapper.add(zoomButton, gbc);

        // Settings Button
        gbc.gridx++;
        gbc.insets = new Insets(0, 0, 0, 6);
        wrapper.add(new AnimatedIconButton("⚙", Theme.BTN_HOVER_SETTINGS, () -> {
            // TODO: Settings action
        }), gbc);

        // Close Button
        gbc.gridx++;
        gbc.insets = new Insets(0, 0, 0, 0);
        wrapper.add(new AnimatedIconButton("✕", Theme.BTN_HOVER_CLOSE, this::handleClose), gbc);

        box.add(wrapper);
        box.add(Box.createHorizontalStrut(10));
        return box;
    }

    @SneakyThrows
    private void handleClose() {
        cefWebView.shutdown(() -> System.exit(0));
    }
}