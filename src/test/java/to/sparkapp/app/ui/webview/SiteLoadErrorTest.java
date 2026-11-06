package to.sparkapp.app.ui.webview;

import java.net.ServerSocket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import to.sparkapp.app.config.AiConfiguration.AiConfig;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.support.JavaFxTestRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("native")
@Timeout(40)
class SiteLoadErrorTest {
    @Test
    void editOnNavigationFailureTargetsTheCurrentlySelectedSite() throws Exception {
        final String unavailable;

        try (var socket = new ServerSocket(0)) {
            unavailable = "http://127.0.0.1:" + socket.getLocalPort();
        }

        var edited = new AtomicReference<AiConfig>();
        var first = new AiConfig("failed-first", "First", unavailable + "/first", null, null);
        var second = new AiConfig("failed-second", "Second", unavailable + "/second", null, null);
        var preferences = JavaFxTestRuntime.call(AppPreferences::new);
        var pane = JavaFxTestRuntime.call(() -> {
            var browser = new FxWebViewPane(first.url(), preferences);
            browser.setOnEditSite(edited::set);
            browser.setCurrentConfig(first, null);
            var stage = new Stage();
            stage.setScene(new Scene(browser, 700, 500));
            stage.setX(-20000);
            stage.setY(-20000);
            stage.show();
            return browser;
        });

        try {
            editFailedSite(pane);
            assertEquals(first, edited.get());
            JavaFxTestRuntime.call(() -> {
                pane.setCurrentConfig(second, null);
                return null;
            });
            editFailedSite(pane);
            assertEquals(second, edited.get());
        } finally {
            var stopped = new CountDownLatch(1);
            JavaFxTestRuntime.call(() -> {
                pane.shutdown(stopped::countDown);
                ((Stage) pane.getScene().getWindow()).close();
                preferences.flush();
                return null;
            });
            assertTrue(stopped.await(5, TimeUnit.SECONDS));
        }
    }

    private void editFailedSite(FxWebViewPane pane) throws Exception {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);

        while (System.nanoTime() < deadline) {
            var failed = JavaFxTestRuntime.call(() -> {
                var overlay = (WebViewLoadingOverlay) pane.getChildren().getFirst();
                return overlay.isActive() && overlay.getAccessibleText().contains("could not be loaded");
            });

            if (failed) {
                JavaFxTestRuntime.call(() -> {
                    var edit = pane.lookupAll(".button").stream().filter(Button.class::isInstance)
                            .map(Button.class::cast).filter(button -> "Edit site".equals(button.getText()))
                            .findFirst().orElseThrow();
                    assertTrue(edit.isVisible());
                    edit.fire();
                    return null;
                });
                return;
            }

            Thread.sleep(30);
        }

        throw new AssertionError("The failed navigation did not show its recovery actions");
    }
}
