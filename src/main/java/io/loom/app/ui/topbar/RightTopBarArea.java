package io.loom.app.ui.topbar;

import io.loom.app.ui.CefWebView;
import io.loom.app.ui.Theme;
import io.loom.app.ui.topbar.components.AnimatedIconButton;
import io.loom.app.ui.topbar.components.ZoomButton;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import javax.swing.*;
import java.awt.*;

@RequiredArgsConstructor
class RightTopBarArea {

    private final CefWebView cefWebView;
    private final Runnable onSettingsToggle;
    private final Runnable onCloseWindow;

    Box buildRightArea() {
        var box = Box.createHorizontalBox();

        var wrapper = new JPanel(new GridBagLayout());
        wrapper.setOpaque(false);

        var gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.CENTER;

        var zoomButton = new ZoomButton(cefWebView::resetZoom);
        cefWebView.setZoomCallback(zoomButton::updateZoomDisplay);
        gbc.insets = new Insets(0, 0, 0, 15);
        wrapper.add(zoomButton, gbc);

        gbc.gridx++;
        gbc.insets = new Insets(0, 0, 0, 6);
        wrapper.add(new AnimatedIconButton("⚙", Theme.BTN_HOVER_SETTINGS, onSettingsToggle), gbc);

        gbc.gridx++;
        gbc.insets = new Insets(0, 0, 0, 0);
        wrapper.add(new AnimatedIconButton("✕", Theme.BTN_HOVER_CLOSE, onCloseWindow), gbc);

        box.add(wrapper);
        box.add(Box.createHorizontalStrut(10));
        return box;
    }
}
