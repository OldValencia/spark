package to.sparkapp.app.ui.webview;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

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
@Timeout(40)
class EarlyPagePresentationTest {
    @Test
    void usableDocumentAppearsBeforeASlowImageCompletesWithoutLosingDraft() throws Exception {
        var releaseImage = new CountDownLatch(1);
        var imageRequested = new CountDownLatch(1);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            server.setExecutor(executor);
            server.createContext("/", exchange -> {
                if (exchange.getRequestURI().getPath().equals("/slow-image")) {
                    imageRequested.countDown();

                    try {
                        releaseImage.await(20, TimeUnit.SECONDS);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }

                    exchange.sendResponseHeaders(204, -1);
                } else {
                    var body = "<!doctype html><textarea id='draft'></textarea><img src='/slow-image'>";
                    var bytes = body.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "text/html");
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                }

                exchange.close();
            });
            server.start();

            try {
                exercisePage(server, imageRequested, releaseImage);
            } finally {
                releaseImage.countDown();
                server.stop(0);
            }
        }
    }

    private void exercisePage(HttpServer server, CountDownLatch imageRequested, CountDownLatch releaseImage) throws Exception {
        var url = "http://127.0.0.1:" + server.getAddress().getPort() + "/page";
        var preferences = JavaFxTestRuntime.call(AppPreferences::new);
        var pane = JavaFxTestRuntime.call(() -> {
            var browser = new FxWebViewPane(url, preferences);
            browser.setCurrentConfig(new AiConfig("early-page", "Early page", url, null, null), null);
            var stage = new Stage();
            stage.setScene(new Scene(browser, 700, 500));
            stage.setX(-20000);
            stage.setY(-20000);
            stage.show();

            return browser;
        });
        var stopped = new CountDownLatch(1);

        try {
            var field = FxWebViewPane.class.getDeclaredField("bridge");
            field.setAccessible(true);
            var browser = (WebviewManager) field.get(pane);
            assertTrue(imageRequested.await(10, TimeUnit.SECONDS));
            await(browser::isPagePresentable);
            await(() -> {
                try {
                    return JavaFxTestRuntime.call(() -> !((WebViewLoadingOverlay) pane.getChildren().getFirst()).isActive());
                } catch (Exception failure) {
                    throw new AssertionError(failure);
                }
            });
            assertTrue(browser.isLoading(), "The native navigation must still be waiting for the image");
            browser.eval("document.querySelector('#draft').value='unsent';history.replaceState({},'', '/page/draft/unsent')");
            await(() -> preferences.getLastUrl() != null && preferences.getLastUrl().endsWith("draft/unsent"));
            releaseImage.countDown();
            await(() -> !browser.isLoading());
            browser.eval("history.replaceState({},'', '/page/kept/'+document.querySelector('#draft').value)");
            await(() -> preferences.getLastUrl() != null && preferences.getLastUrl().endsWith("kept/unsent"));
            assertFalse(browser.isLoading());
            System.out.println("Usable document exposed while image remained pending; draft survived completion.");
        } finally {
            releaseImage.countDown();
            JavaFxTestRuntime.call(() -> {
                pane.shutdown(stopped::countDown);
                ((Stage) pane.getScene().getWindow()).close();
                preferences.flush();

                return null;
            });
            assertTrue(stopped.await(5, TimeUnit.SECONDS));
        }
    }

    private void await(BooleanSupplier condition) throws Exception {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);

        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }

            Thread.sleep(20);
        }

        throw new AssertionError("The page did not reach its expected state");
    }
}
