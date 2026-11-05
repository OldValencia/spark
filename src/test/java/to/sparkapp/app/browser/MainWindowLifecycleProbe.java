package to.sparkapp.app.browser;

import com.sun.net.httpserver.HttpServer;
import javafx.application.Platform;
import to.sparkapp.app.config.AiConfiguration;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.windows.MainWindow;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Callable;

/** Subprocess fixture: exercise normal application shutdown without calling System.exit. */
public final class MainWindowLifecycleProbe {
    public static void main(String[] args) throws Exception {
        if (!"true".equals(System.getProperty("spark.nativeTests"))) throw new IllegalStateException("Test profile required");
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            var body = "<!doctype html><title>Shutdown fixture</title><p>Loaded</p>".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        var boot = new CountDownLatch(1);
        Platform.startup(() -> { Platform.setImplicitExit(false); boot.countDown(); });
        if (!boot.await(10, TimeUnit.SECONDS)) throw new AssertionError("JavaFX did not start");
        var window = fx(() -> {
            var preferences = new AppPreferences();
            preferences.setCheckUpdatesOnStartup(false);
            preferences.setStartApplicationHiddenEnabled(true);
            var sites = new AiConfiguration(preferences);
            sites.getCustomProvidersManager().saveProviders(List.of(new AiConfiguration.AiConfig(
                    "shutdown-fixture", "Fixture", "http://127.0.0.1:" + server.getAddress().getPort(), "#777777", "chatgpt.svg")));
            sites.reload();
            var main = new MainWindow(sites, preferences);
            main.showWindow();
            return main;
        });
        // initializeOnFX was queued by showWindow before this task.
        fx(() -> { window.setX(-20000); window.setY(-20000); window.showMainWindow(); return null; });
        var paneField = MainWindow.class.getDeclaredField("fxWebViewPane");
        paneField.setAccessible(true);
        Object pane = fx(() -> paneField.get(window));
        var browserField = pane.getClass().getDeclaredField("bridge");
        browserField.setAccessible(true);
        var browser = (WebviewManager) fx(() -> browserField.get(pane));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (browser.isLoading() && System.nanoTime() < deadline) Thread.sleep(20);
        server.stop(0);
        if (browser.isLoading() || browser.getLoadError() != null) throw new AssertionError("Fixture did not load");
        var shutdown = MainWindow.class.getDeclaredMethod("performShutdown");
        shutdown.setAccessible(true);
        fx(() -> { shutdown.invoke(window); return null; });
        System.out.println("NORMAL_SHUTDOWN_REQUESTED");
        // Returning from main must allow the JVM to exit after JavaFX/native cleanup.
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        var task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(15, TimeUnit.SECONDS);
    }
}
