package io.loom.app.ui;

import io.loom.app.config.AiConfiguration;
import io.loom.app.config.AppPreferences;
import io.loom.app.utils.LogSetup;
import io.loom.app.utils.MemoryMonitor;
import io.loom.app.windows.MainWindow;
import io.loom.app.windows.SettingsWindow;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import me.friwi.jcefmaven.CefAppBuilder;
import me.friwi.jcefmaven.CefInitializationException;
import me.friwi.jcefmaven.UnsupportedPlatformException;
import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.CefSettings;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.callback.CefContextMenuParams;
import org.cef.callback.CefMenuModel;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.*;
import org.cef.misc.BoolRef;
import org.cef.network.CefCookieManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.function.Consumer;

@Slf4j
public class CefWebView extends JPanel {

    private final String BASE_DIR = AppPreferences.DATA_DIR.getAbsolutePath();

    private final String LOGS_DIR = LogSetup.LOGS_DIR;
    private final String INSTALL_DIR = new File(BASE_DIR, "jcef-bundle").getAbsolutePath();
    private final String CACHE_DIR = new File(BASE_DIR, "cache").getAbsolutePath();
    private final String CEF_LOG_FILE = new File(LOGS_DIR, "cef.log").getAbsolutePath();

    private static final String ZOOM_JS = """
            document.addEventListener('wheel', function(e) {
               if(e.ctrlKey) {
                   e.preventDefault();
                   window.cefQuery({request: 'zoom_scroll:' + e.deltaY});
               }
            }, {passive: false});
            """;

    private final JLayeredPane layeredPane;

    private final AppPreferences appPreferences;
    private final Runnable onToggleSettings;
    private final Consumer<String> onProgressUpdate;

    @Setter
    private Consumer<Double> zoomCallback;
    @Setter
    private SettingsWindow settingsWindow;

    private CefClient client;
    private CefBrowser browser;
    private AiConfiguration.AiConfig currentConfig;
    private MemoryMonitor memoryMonitor;
    private double currentZoomLevel;

    public CefWebView(String startUrl, AppPreferences appPreferences, Runnable onToggleSettings, Consumer<String> onProgressUpdate) {
        this.appPreferences = appPreferences;
        this.currentZoomLevel = appPreferences.getLastZoomValue();
        this.onToggleSettings = onToggleSettings;
        this.onProgressUpdate = onProgressUpdate;

        setLayout(new BorderLayout());
        setBackground(Theme.BG_DEEP);

        layeredPane = new JLayeredPane();
        layeredPane.setLayout(null);
        add(layeredPane, BorderLayout.CENTER);

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                updateLayerBounds();
            }
        });

        smartClean();
        initCef(startUrl);
    }

    public void setCurrentConfig(AiConfiguration.AiConfig currentConfig) {
        this.currentConfig = currentConfig;
        loadUrl(currentConfig.url());
    }

    public void restart() {
        if (browser == null || client == null) {
            return;
        }

        var urlToRestore = (currentConfig != null) ? currentConfig.url() : browser.getURL();

        if (browser.getUIComponent() != null) {
            layeredPane.remove(browser.getUIComponent());
        }

        browser.close(true);
        browser = null;
        System.gc();

        // Small delay to ensure cleanup
        SwingUtilities.invokeLater(() -> {
            browser = client.createBrowser(urlToRestore, false, false);

            var newBrowserUI = browser.getUIComponent();
            newBrowserUI.setBounds(0, 0, getWidth(), getHeight());
            layeredPane.add(newBrowserUI, JLayeredPane.DEFAULT_LAYER);

            updateLayerBounds();
            layeredPane.revalidate();
            layeredPane.repaint();

            log.info("Browser engine restarted.");
        });
    }

    private void updateLayerBounds() {
        if (browser != null) {
            var browserUI = browser.getUIComponent();
            browserUI.setBounds(0, 0, getWidth(), getHeight());
        }

        layeredPane.revalidate();
        layeredPane.repaint();
    }

    private void smartClean() {
        var bundleDir = new File(INSTALL_DIR);
        if (bundleDir.exists()) {
            try (var walkStream = Files.walk(bundleDir.toPath())) {
                walkStream.filter(p -> p.toFile().getName().equals("locales") && p.toFile().isDirectory())
                        .findFirst()
                        .ifPresent(localesPath -> {
                            var files = localesPath.toFile().listFiles();
                            if (files != null) {
                                for (var f : files) {
                                    var name = f.getName();
                                    if (!name.equals("en-US.pak")) {
                                        f.delete();
                                    }
                                }
                            }
                        });
            } catch (Exception e) {
                log.error("Can't delete locales", e);
            }
        }

        var cache = new File(CACHE_DIR);
        if (cache.exists()) {
            var files = cache.listFiles();
            if (files != null) {
                for (var f : files) {
                    var name = f.getName();
                    if (name.equals("Cache") || name.equals("Code Cache") ||
                        name.equals("GPUCache") || name.equals("ScriptCache") ||
                        name.equals("Service Worker") || name.equals("blob_storage")) {
                        deleteDirectory(f);
                    }
                }
            }
        }
    }

    private void deleteDirectory(File dir) {
        if (dir.isDirectory()) {
            var entries = dir.listFiles();
            if (entries != null) {
                for (var entry : entries) {
                    deleteDirectory(entry);
                }
            }
        }
        dir.delete();
    }

    private void initCef(String startUrl) {
        try {
            var builder = new CefAppBuilder();

            var installDirFile = new File(INSTALL_DIR);
            if (!installDirFile.exists()) {
                if (installDirFile.mkdirs()) {
                    log.info("Created JCEF install dir: {}", INSTALL_DIR);
                } else {
                    log.error("Failed to create JCEF install dir: {}", INSTALL_DIR);
                }
            }
            builder.setInstallDir(installDirFile);

            builder.setProgressHandler((state, percent) -> {
                var statusText = switch (state) {
                    case LOCATING -> "Locating browser engine...";
                    case DOWNLOADING -> "Downloading browser engine... " + Math.round(percent) + "%";
                    case EXTRACTING -> "Extracting browser engine... " + Math.round(percent * -100) + "%";
                    case INSTALL -> "Installing browser engine...";
                    default -> "Initializing...";
                };

                if (onProgressUpdate != null) {
                    SwingUtilities.invokeLater(() -> onProgressUpdate.accept(statusText));
                }
                log.info("CEF Init Progress: {} - {}%", state, percent);
            });

            // Reduced renderer processes and memory limits
            builder.addJcefArgs("--renderer-process-limit=1");
            builder.addJcefArgs("--process-per-site");
            builder.addJcefArgs("--disk-cache-size=3145728");  // 3MB
            builder.addJcefArgs("--disable-gpu-shader-disk-cache");
            builder.addJcefArgs("--enable-low-end-device-mode");
            builder.addJcefArgs("--aggressive-cache-discard");
            builder.addJcefArgs("--disable-background-networking");
            builder.addJcefArgs("--disable-sync");
            builder.addJcefArgs("--disable-breakpad");
            builder.addJcefArgs("--disable-component-update");
            builder.addJcefArgs("--disable-webgl");
            builder.addJcefArgs("--disable-accelerated-2d-canvas");
            builder.addJcefArgs("--disable-accelerated-video-decode");
            builder.addJcefArgs("--disable-software-rasterizer");
            builder.addJcefArgs("--disable-dev-shm-usage");

            // Reduced JS heap size
            builder.addJcefArgs("--js-flags=--max-old-space-size=64");
            builder.addJcefArgs("--js-flags=--initial-heap-size=8");
            builder.addJcefArgs("--js-flags=--optimize-for-size");
            builder.addJcefArgs("--js-flags=--expose-gc");

            builder.addJcefArgs("--disable-http-cache");
            builder.addJcefArgs("--disable-features=AutofillServerCommunication");
            builder.addJcefArgs("--disable-features=TranslateUI");
            builder.addJcefArgs("--disable-features=MediaRouter");
            builder.addJcefArgs("--disable-skia-runtime-opts");
            builder.addJcefArgs("--disable-lcd-text");
            builder.addJcefArgs("--disable-image-animation-resync");

            // Memory pressure handling
            builder.addJcefArgs("--enable-features=MemoryPressureBased");

            if (appPreferences.isDarkModeEnabled()) {
                builder.addJcefArgs("--force-dark-mode");
                builder.addJcefArgs("--enable-features=WebUIDarkMode");
                builder.addJcefArgs("--ui-disable-partial-swap");
                builder.addJcefArgs("--blink-settings=darkModeEnabled=true,forceDarkModeEnabled=true");
            }

            configureSettings(builder);

            if (onProgressUpdate != null) {
                onProgressUpdate.accept("Building browser engine...");
            }

            var app = builder.build();
            client = app.createClient();

            setupZoomHandler();
            setupKeyboardHandler();
            setupLoadHandler();
            setupContextMenuHandler();

            setupDisplayHandler();

            browser = client.createBrowser(startUrl, true, false);

            var browserUI = browser.getUIComponent();
            layeredPane.add(browserUI, JLayeredPane.DEFAULT_LAYER);

            memoryMonitor = new MemoryMonitor(browser);
            memoryMonitor.start();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    if (memoryMonitor != null) {
                        memoryMonitor.stop();
                    }
                    CefApp.getInstance().dispose();
                } catch (Exception ignored) {
                }
            }));

            SwingUtilities.invokeLater(() -> {
                updateLayerBounds();
                if (browser != null) {
                    browser.setZoomLevel(currentZoomLevel);
                    updateZoomDisplay(currentZoomLevel);
                }
            });

            log.info("JCEF Initialized");

        } catch (IOException | UnsupportedPlatformException | InterruptedException | CefInitializationException e) {
            log.error("Failed to init JCEF", e);
        }
    }

    private void configureSettings(CefAppBuilder builder) {
        var settings = builder.getCefSettings();
        settings.windowless_rendering_enabled = true;
        settings.cache_path = CACHE_DIR;
        settings.root_cache_path = CACHE_DIR;
        settings.persist_session_cookies = true;
        settings.command_line_args_disabled = false;
        settings.log_file = CEF_LOG_FILE;
        settings.log_severity = CefSettings.LogSeverity.LOGSEVERITY_WARNING;
        settings.user_agent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    }

    private void setupDisplayHandler() {
        client.addDisplayHandler(new CefDisplayHandlerAdapter() {
            @Override
            public void onAddressChange(CefBrowser browser, CefFrame frame, String url) {
                checkIfAuthPage(url);
            }
        });
    }

    private void checkIfAuthPage(String url) {
        if (url == null) {
            return;
        }
        var lowerUrl = url.toLowerCase();
        var isAuth = lowerUrl.contains("accounts.google.com") ||
                         lowerUrl.contains("appleid.apple.com") ||
                         lowerUrl.contains("github.com/login") ||
                         lowerUrl.contains("oauth") ||
                         lowerUrl.contains("signin") ||
                         lowerUrl.contains("login");

        var parent = SwingUtilities.getWindowAncestor(this);
        if (parent instanceof MainWindow mainWindow) {
            mainWindow.setAuthMode(isAuth);
        }
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
                        log.error("Error while trying to handle zoom/scroll request", e);
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
                if (!appPreferences.isZoomEnabled() || (event.modifiers & 2) == 0) {
                    return false;
                }
                var isPressed = event.type == CefKeyEvent.EventType.KEYEVENT_RAWKEYDOWN;
                int code = event.windows_key_code;
                if (code == 187 || code == 61 || code == 107) {
                    if (isPressed) {
                        changeZoom(true);
                    }
                    return true;
                }
                if (code == 189 || code == 45 || code == 109) {
                    if (isPressed) {
                        changeZoom(false);
                    }
                    return true;
                }
                if (code == 48 || code == 96) {
                    if (isPressed) {
                        resetZoom();
                    }
                    return true;
                }
                return false;
            }
        });
    }

    private void setupContextMenuHandler() {
        client.addContextMenuHandler(new CefContextMenuHandlerAdapter() {
            @Override
            public void onBeforeContextMenu(CefBrowser browser, CefFrame frame, CefContextMenuParams params, CefMenuModel model) {
                model.clear();
            }
        });
    }

    private void setupLoadHandler() {
        client.addLoadHandler(new CefLoadHandlerAdapter() {
            @Override
            public void onLoadEnd(CefBrowser browser, CefFrame frame, int httpStatusCode) {
                if (frame.isMain()) {
                    browser.executeJavaScript(ZOOM_JS, frame.getURL(), 0);
                    browser.setZoomLevel(currentZoomLevel);
                }
            }
        });
    }

    public void setZoomEnabled(boolean enabled) {
        appPreferences.setZoomEnabled(enabled);
        if (!enabled) {
            resetZoom();
        }
    }

    public void resetZoom() {
        setZoomInternal(0.0);
    }

    public void clearCookies() {
        CefCookieManager.getGlobalManager().deleteCookies("", "");
        restart();
    }

    public void shutdown(Runnable onComplete) {
        CefCookieManager.getGlobalManager().flushStore(() -> {
            dispose();
            onComplete.run();

            new Timer(500, e -> {
                ((Timer) e.getSource()).stop();
                onComplete.run();
                System.exit(0);
            }).start();
        });
    }

    public void dispose() {
        if (memoryMonitor != null) {
            memoryMonitor.stop();
            memoryMonitor = null;
        }
        if (browser != null) {
            browser.close(true);
            browser = null;
        }
        if (client != null) {
            client.dispose();
            client = null;
        }
        System.gc();
    }

    private void loadUrl(String url) {
        if (browser != null) {
            browser.loadURL(url);
            if (settingsWindow != null && settingsWindow.isOpen()) {
                onToggleSettings.run();
            }
        }
    }

    private void changeZoom(boolean increase) {
        var step = 0.5;
        var newLevel = currentZoomLevel + (increase ? step : -step);
        newLevel = Math.max(-3.0, Math.min(4.0, newLevel));
        setZoomInternal(newLevel);
    }

    private void setZoomInternal(double level) {
        this.currentZoomLevel = level;
        appPreferences.setLastZoomValue(level);

        if (browser != null) {
            browser.setZoomLevel(level);
        }

        updateZoomDisplay(level);
    }

    private void updateZoomDisplay(double level) {
        if (zoomCallback != null) {
            var percentage = Math.pow(1.2, level) * 100;
            var displayVal = Math.round(percentage / 5.0) * 5;
            zoomCallback.accept((double) displayVal);
        }
    }
}
