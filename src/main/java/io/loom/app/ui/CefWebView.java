package io.loom.app.ui;

import io.loom.app.browser.NativeWebViewBridge;
import io.loom.app.config.AiConfiguration;
import io.loom.app.config.AppPreferences;
import io.loom.app.windows.MainWindow;
import io.loom.app.windows.SettingsWindow;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.HierarchyEvent;
import java.util.function.Consumer;

@Slf4j
public class CefWebView extends JPanel {

    private final NativeWebViewBridge bridge;
    private final AppPreferences appPreferences;
    private final Runnable onToggleSettings;

    @Setter
    private SettingsWindow settingsWindow;
    @Setter
    private Consumer<Double> zoomCallback;

    private boolean bridgeStarted = false;
    private final String startUrl;

    public CefWebView(String startUrl,
                      AppPreferences appPreferences,
                      Runnable onToggleSettings,
                      Consumer<String> onProgressUpdate) {

        this.startUrl = startUrl;
        this.appPreferences = appPreferences;
        this.onToggleSettings = onToggleSettings;

        setBackground(Theme.BG_DEEP);
        setLayout(new BorderLayout());

        bridge = new NativeWebViewBridge(appPreferences);

        bridge.setZoomCallback(pct -> {
            if (zoomCallback != null) {
                zoomCallback.accept(pct);
            }
        });

        bridge.setOnUrlChanged(url -> {
            checkIfAuthPage(url);
            if (appPreferences.isRememberLastAi()) {
                appPreferences.setLastUrl(url);
            }
        });

        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) {
                startBridgeIfNeeded();
            }
        });

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                syncBounds();
            }

            @Override
            public void componentMoved(ComponentEvent e) {
                syncBounds();
            }

            @Override
            public void componentShown(ComponentEvent e) {
                bridge.setVisible(true);
                syncBounds();
            }

            @Override
            public void componentHidden(ComponentEvent e) {
                bridge.setVisible(false);
            }
        });
    }

    private synchronized void startBridgeIfNeeded() {
        if (bridgeStarted) {
            return;
        }
        bridgeStarted = true;

        Point screenPt = getLocationOnScreen();
        int w = Math.max(getWidth(), 400);
        int h = Math.max(getHeight(), 300);

        bridge.init(startUrl, screenPt.x, screenPt.y, w, h);

        Window ancestor = SwingUtilities.getWindowAncestor(this);
        if (ancestor != null) {
            ancestor.addComponentListener(new ComponentAdapter() {
                @Override
                public void componentMoved(ComponentEvent e) {
                    syncBounds();
                }

                @Override
                public void componentResized(ComponentEvent e) {
                    syncBounds();
                }

                @Override
                public void componentShown(ComponentEvent e) {
                    bridge.setVisible(true);
                    syncBounds();
                }

                @Override
                public void componentHidden(ComponentEvent e) {
                    bridge.setVisible(false);
                }
            });
        }

        log.info("NativeWebViewBridge started — url={}", startUrl);
    }

    private void syncBounds() {
        if (!bridgeStarted || !isShowing()) {
            return;
        }
        try {
            Point pt = getLocationOnScreen();
            bridge.updateBounds(pt.x, pt.y, getWidth(), getHeight());
        } catch (IllegalComponentStateException ignored) {
        }
    }

    public void setCurrentConfig(AiConfiguration.AiConfig config) {
        bridge.setCurrentConfig(config);
        if (settingsWindow != null && settingsWindow.isOpen()) {
            onToggleSettings.run();
        }
    }

    public void setVisible(boolean visible) {
        super.setVisible(visible);
        bridge.setVisible(visible);
    }

    public void resetZoom() {
        bridge.resetZoom();
    }

    public void setZoomEnabled(boolean enabled) {
        bridge.setZoomEnabled(enabled);
    }

    public void clearCookies() {
        bridge.clearCookies();
    }

    public void shutdown(Runnable onComplete) {
        bridge.shutdown(() -> SwingUtilities.invokeLater(() -> {
            onComplete.run();
            new Timer(300, e -> {
                ((Timer) e.getSource()).stop();
                System.exit(0);
            }).start();
        }));
    }

    public void restart() {
        var url = appPreferences.getLastUrl() != null ? appPreferences.getLastUrl() : startUrl;
        bridge.navigate(url);
    }

    private void checkIfAuthPage(String url) {
        if (url == null) {
            return;
        }
        String lower = url.toLowerCase();
        boolean isAuth = lower.contains("accounts.google.com")
                || lower.contains("appleid.apple.com")
                || lower.contains("github.com/login")
                || lower.contains("oauth")
                || lower.contains("signin")
                || lower.contains("login");

        Window parent = SwingUtilities.getWindowAncestor(this);
        if (parent instanceof MainWindow mainWindow) {
            mainWindow.setAuthMode(isAuth);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        g.setColor(Theme.BG_DEEP);
        g.fillRect(0, 0, getWidth(), getHeight());
    }
}
