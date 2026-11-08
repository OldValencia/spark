package to.sparkapp.app.ui.webview;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.sun.net.httpserver.HttpServer;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import to.sparkapp.app.browser.WebviewManager;
import to.sparkapp.app.config.AiConfiguration.AiConfig;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.support.JavaFxTestRuntime;

@Tag("native")
@Timeout(60)
class BrowserPresentationTest {
    @Test
    void focusRequestSurvivesShowingThePageAgainButNotHidingIt() throws Exception {
        JavaFxTestRuntime.start();

        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            var bytes = "<!doctype html><title>Focus</title><textarea></textarea>".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        var site = new AiConfig("focus-site", "Focus", "http://127.0.0.1:" + server.getAddress().getPort() + "/", null,
                null);
        var stage = fx(Stage::new);
        var pane = fx(() -> new FxWebViewPane(site.url(), new AppPreferences()));

        try {
            fx(() -> {
                stage.setScene(new Scene(pane, 500, 400));
                stage.setX(-20000);
                stage.setY(-20000);
                pane.setCurrentConfig(site, null);
                stage.show();

                return null;
            });
            var browser = active(pane);
            await(() -> !browser.isLoading() && browser.isPagePresentable());

            // Switching sites presents the page more than once; the second time must not drop the request.
            var shown = new CompletableFuture<Boolean>();
            fx(() -> {
                browser.focusBrowser(shown::complete);
                browser.setVisible(true);

                return null;
            });
            assertNotNull(shown.get(10, TimeUnit.SECONDS), "Focus must reach a page that is still on screen");

            var hidden = new CompletableFuture<Boolean>();
            fx(() -> {
                browser.focusBrowser(hidden::complete);
                browser.setVisible(false);

                return null;
            });
            assertThrows(TimeoutException.class, () -> hidden.get(1, TimeUnit.SECONDS),
                    "A page hidden after the request must not take focus");
        } finally {
            var closed = new CompletableFuture<Void>();
            fx(() -> {
                stage.hide();
                pane.shutdown(() -> closed.complete(null));

                return null;
            });
            closed.get(15, TimeUnit.SECONDS);
            server.stop(0);
        }
    }

    private static WebviewManager active(FxWebViewPane pane) throws Exception {
        Field field = FxWebViewPane.class.getDeclaredField("bridge");
        field.setAccessible(true);

        return (WebviewManager) field.get(pane);
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        var task = new FutureTask<>(action);
        Platform.runLater(task);

        return task.get(10, TimeUnit.SECONDS);
    }

    private static void await(Callable<Boolean> condition) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);

        while (!condition.call() && System.nanoTime() < until) {
            Thread.sleep(20);
        }

        assertTrue(condition.call(), "Timed out waiting for the page");
    }

    @Test
    void slowPageReportsLoadingUntilItFinishes() throws Exception {
        JavaFxTestRuntime.start();

        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            if (exchange.getRequestURI().getPath().equals("/slow.png")) {
                sleep(1500);
            }

            var body = "<!doctype html><title>Slow</title><img src='/slow.png'>";
            var bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        var site = new AiConfig("loading-site", "Loading", "http://127.0.0.1:" + server.getAddress().getPort() + "/",
                null, null);
        var stage = fx(Stage::new);
        var pane = fx(() -> new FxWebViewPane(site.url(), new AppPreferences()));
        var sawLoading = new java.util.concurrent.atomic.AtomicBoolean();

        try {
            fx(() -> {
                pane.pageLoadingProperty().addListener((o, was, loading) -> sawLoading.compareAndSet(false, loading));
                stage.setScene(new Scene(pane, 500, 400));
                stage.setX(-20000);
                stage.setY(-20000);
                pane.setCurrentConfig(site, null);
                stage.show();

                return null;
            });
            var browser = active(pane);
            await(() -> !browser.isLoading() && browser.isPagePresentable());
            await(() -> !fx(() -> pane.pageLoadingProperty().get()));

            assertTrue(sawLoading.get(), "The page must report loading while its image is still arriving");

            fx(() -> {
                pane.onWindowHidden();

                return null;
            });
            assertFalse(fx(() -> pane.pageLoadingProperty().get()));
        } finally {
            var closed = new CompletableFuture<Void>();
            fx(() -> {
                stage.hide();
                pane.shutdown(() -> closed.complete(null));

                return null;
            });
            closed.get(15, TimeUnit.SECONDS);
            server.stop(0);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void onlyASiteThatIsNotOnScreenReportsNews() throws Exception {
        JavaFxTestRuntime.start();

        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            var bytes = "<!doctype html><title>Start</title>".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        var base = "http://127.0.0.1:" + server.getAddress().getPort();
        var first = new AiConfig("news-first", "First", base + "/first", null, null);
        var second = new AiConfig("news-second", "Second", base + "/second", null, null);
        var reported = new java.util.concurrent.LinkedBlockingQueue<String>();
        var stage = fx(Stage::new);
        var pane = fx(() -> new FxWebViewPane(first.url(), new AppPreferences()));

        try {
            fx(() -> {
                pane.setOnSiteUpdated(reported::add);
                stage.setScene(new Scene(pane, 500, 400));
                stage.setX(-20000);
                stage.setY(-20000);
                pane.setCurrentConfig(first, null);
                stage.show();

                return null;
            });
            var firstBrowser = active(pane);
            await(() -> !firstBrowser.isLoading() && firstBrowser.isPagePresentable());

            fx(() -> {
                pane.setCurrentConfig(second, null);

                return null;
            });
            var secondBrowser = active(pane);
            await(() -> !secondBrowser.isLoading() && secondBrowser.isPagePresentable());
            firstBrowser.eval("document.title='Too early'");
            Thread.sleep(500);
            assertNull(reported.poll(), "A title set right after the first load is not news");

            Thread.sleep(2000);
            secondBrowser.eval("document.title='Seen'");
            firstBrowser.eval("document.title='News'");

            assertEquals("news-first", reported.poll(10, TimeUnit.SECONDS));
            Thread.sleep(500);
            assertNull(reported.poll(), "The site on screen has no news");
        } finally {
            var closed = new CompletableFuture<Void>();
            fx(() -> {
                stage.hide();
                pane.shutdown(() -> closed.complete(null));

                return null;
            });
            closed.get(15, TimeUnit.SECONDS);
            server.stop(0);
        }
    }
}
