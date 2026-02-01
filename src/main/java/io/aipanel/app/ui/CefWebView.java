package io.aipanel.app.ui;

import io.aipanel.app.config.AppPreferences;
import io.aipanel.app.utils.LogSetup;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import me.friwi.jcefmaven.CefAppBuilder;
import me.friwi.jcefmaven.CefInitializationException;
import me.friwi.jcefmaven.UnsupportedPlatformException;
import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.callback.CefContextMenuParams;
import org.cef.callback.CefMenuModel;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefContextMenuHandlerAdapter;
import org.cef.handler.CefKeyboardHandlerAdapter;
import org.cef.handler.CefLoadHandlerAdapter;
import org.cef.handler.CefMessageRouterHandlerAdapter;
import org.cef.misc.BoolRef;
import org.cef.network.CefCookieManager;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

@Slf4j
public class CefWebView extends JPanel {

    // Paths
    private static final String BASE_DIR = new File(System.getProperty("user.home"), ".aipanel").getAbsolutePath();
    private static final String INSTALL_DIR = new File(BASE_DIR, "jcef-bundle").getAbsolutePath();
    private static final String CACHE_DIR = new File(BASE_DIR, "cache").getAbsolutePath();
    private static final String CEF_LOG_FILE = new File(LogSetup.LOGS_DIR, "cef.log").getAbsolutePath();

    // JS Injection
    private static final String ZOOM_JS = """
            document.addEventListener('wheel', function(e) {
               if(e.ctrlKey) {
                   e.preventDefault();
                   window.cefQuery({request: 'zoom_scroll:' + e.deltaY});
               }
            }, {passive: false});
            """;

    private CefClient client;
    private CefBrowser browser;

    @Setter
    private Consumer<Double> zoomCallback;

    private final AppPreferences appPreferences;

    public CefWebView(String startUrl, AppPreferences appPreferences) {
        this.appPreferences = appPreferences;

        setLayout(new BorderLayout());
        setBackground(Theme.BG_DEEP);
        initCef(startUrl);
    }

    public void setZoomEnabled(boolean enabled) {
        appPreferences.setZoomEnabled(enabled);
        if (!appPreferences.isZoomEnabled()) {
            resetZoom();
        }
    }

    public void resetZoom() {
        setZoomInternal(0.0);
    }

    public void loadUrl(String url) {
        if (browser != null) browser.loadURL(url);
    }

    public void clearCookies() {
        CefCookieManager.getGlobalManager().deleteCookies("", "");
    }

    public void shutdown(Runnable onComplete) {
        CefCookieManager.getGlobalManager().flushStore(() -> {
            dispose();
            onComplete.run();
        });
    }

    public void dispose() {
        if (browser != null) {
            browser.close(true);
        }
        if (client != null) {
            client.dispose();
        }
    }

    private void initCef(String startUrl) {
        try {
            // 1. Configure CefApp
            var builder = new CefAppBuilder();
            builder.setInstallDir(new File(INSTALL_DIR));
            configureSettings(builder);

            var app = builder.build();
            client = app.createClient();

            // 2. Attach Handlers
            setupZoomHandler();
            setupKeyboardHandler();
            setupLoadHandler();
            setupContextMenuHandler();

            // 3. Create Browser
            browser = client.createBrowser(startUrl, false, false);
            add(browser.getUIComponent(), BorderLayout.CENTER);

            // 4. Register Shutdown Hook
            Runtime.getRuntime().addShutdownHook(new Thread(this::performShutdown));

            revalidate();
            log.info("JCEF Initialized successfully. Install dir: {}", INSTALL_DIR);
        } catch (IOException | UnsupportedPlatformException | InterruptedException | CefInitializationException e) {
            log.error("Failed to initialize JCEF", e);
        }
    }

    private void configureSettings(CefAppBuilder builder) {
        var settings = builder.getCefSettings();
        settings.windowless_rendering_enabled = false;
        settings.cache_path = CACHE_DIR;
        settings.root_cache_path = CACHE_DIR;
        settings.persist_session_cookies = true;
        settings.log_file = CEF_LOG_FILE;
        settings.log_severity = org.cef.CefSettings.LogSeverity.LOGSEVERITY_WARNING;
        settings.user_agent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    }

    private void setupZoomHandler() {
        var msgRouter = CefMessageRouter.create();
        msgRouter.addHandler(new CefMessageRouterHandlerAdapter() {
            @Override
            public boolean onQuery(CefBrowser browser, CefFrame frame, long queryId, String request, boolean persistent, CefQueryCallback callback) {
                if (request.startsWith("zoom_scroll:") && appPreferences.isZoomEnabled()) {
                    try {
                        var delta = Double.parseDouble(request.split(":")[1]);
                        changeZoom(delta < 0);
                    } catch (Exception e) {
                        log.error("Can't parse request in a zoom handler", e);
                    }
                    return true;
                }
                return false;
            }
        }, true);
        client.addMessageRouter(msgRouter);
    }

    private void setupKeyboardHandler() {
        client.addKeyboardHandler(new CefKeyboardHandlerAdapter() {
            @Override
            public boolean onPreKeyEvent(CefBrowser browser, CefKeyEvent event, BoolRef is_keyboard_shortcut) {
                if (!appPreferences.isZoomEnabled() || (event.modifiers & 2) == 0) return false; // Not Ctrl

                boolean isPressed = event.type == CefKeyEvent.EventType.KEYEVENT_RAWKEYDOWN;
                int code = event.windows_key_code;

                // Zoom In (+, =, Numpad +)
                if (code == 187 || code == 61 || code == 107) {
                    if (isPressed) changeZoom(true);
                    return true;
                }
                // Zoom Out (-, _, Numpad -)
                if (code == 189 || code == 45 || code == 109) {
                    if (isPressed) changeZoom(false);
                    return true;
                }
                // Reset (0, Numpad 0)
                if (code == 48 || code == 96) {
                    if (isPressed) resetZoom();
                    return true;
                }
                return false;
            }
        });
    }

    private void setupLoadHandler() {
        client.addLoadHandler(new CefLoadHandlerAdapter() {
            @Override
            public void onLoadEnd(CefBrowser browser, CefFrame frame, int httpStatusCode) {
                if (frame.isMain()) {
                    browser.executeJavaScript(ZOOM_JS, frame.getURL(), 0);
                    browser.setZoomLevel(appPreferences.getLastZoomValue());
                    setZoomInternal(appPreferences.getLastZoomValue());
                }
            }
        });
    }

    private void setupContextMenuHandler() {
        // Disable default context menu
        client.addContextMenuHandler(new CefContextMenuHandlerAdapter() {
            @Override
            public void onBeforeContextMenu(CefBrowser browser, CefFrame frame, CefContextMenuParams params, CefMenuModel model) {
                model.clear();
            }
        });
    }

    private void performShutdown() {
        log.info("Shutting down JCEF...");
        try {
            CefApp.getInstance().dispose();
        } catch (Exception e) {
            log.error("Exception while shutting down JCEF", e);
        }
    }

    private void changeZoom(boolean increase) {
        double step = 0.5;
        double newLevel = appPreferences.getLastZoomValue() + (increase ? step : -step);
        newLevel = Math.max(-3.0, Math.min(4.0, newLevel)); // Clamp -3 to 4
        setZoomInternal(newLevel);
    }

    private void setZoomInternal(double level) {
        appPreferences.setLastZoomValue(level);
        if (browser != null) {
            browser.setZoomLevel(level);
        }
        if (zoomCallback != null) {
            double percentage = Math.pow(1.2, level) * 100;
            long displayVal = Math.round(percentage / 5.0) * 5;
            zoomCallback.accept((double) displayVal);
        }
    }
}