package to.sparkapp.app.ui.topbar.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.mouse.NativeMouseMotionListener;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import javax.swing.event.EventListenerList;

import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.robot.Robot;
import javafx.stage.Stage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import to.sparkapp.app.config.AiConfiguration.AiConfig;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.support.JavaFxTestRuntime;
import to.sparkapp.app.ui.webview.FxWebViewPane;

@Tag("native")
@Timeout(40)
class MascotNativePointerTest {
    @Test
    void nativePageMovementUpdatesTheGazeAndDisabledStatesRemoveTheListener() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            var bytes = "<!doctype html><body style='background:#f6f5f1'>Local pointer fixture</body>"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        boolean registerHook = !GlobalScreen.isNativeHookRegistered();

        if (registerHook) {
            GlobalScreen.registerNativeHook();
        }

        var preferences = JavaFxTestRuntime.call(AppPreferences::new);
        boolean previousMotion = preferences.isReducedMotion();
        boolean previousMascot = preferences.isMascotEnabled();
        var previousPointer = JavaFxTestRuntime.call(() -> new Robot().getMousePosition());
        var sceneMoves = new AtomicInteger();
        var url = "http://127.0.0.1:" + server.getAddress().getPort();
        var stage = JavaFxTestRuntime.call(() -> {
            preferences.setReducedMotion(false);
            preferences.setMascotEnabled(true);
            var window = new Stage();
            var mascot = new SparkMascot(window, preferences);
            var header = new StackPane(mascot);
            header.setPrefHeight(52);
            var page = new FxWebViewPane(url, preferences);
            page.setCurrentConfig(new AiConfig("mascot-pointer", "Local fixture", url, null, null), null);
            var root = new BorderPane(page, header, null, null, null);
            window.setScene(new Scene(root, 600, 400));
            window.getScene().addEventFilter(MouseEvent.MOUSE_MOVED, event -> sceneMoves.incrementAndGet());
            window.setAlwaysOnTop(true);
            window.centerOnScreen();
            window.show();

            return window;
        });
        var root = JavaFxTestRuntime.call(() -> (BorderPane) stage.getScene().getRoot());
        var mascot = JavaFxTestRuntime.call(() -> (SparkMascot) ((StackPane) root.getTop()).getChildren().getFirst());
        var eyes = JavaFxTestRuntime.call(() -> (Group) ((Group) mascot.getGraphic()).getChildren().getLast());
        var page = (FxWebViewPane) root.getCenter();

        try {
            await(() -> !page.getChildren().getFirst().isVisible());
            move(stage, 70);
            await(() -> eyes.getTranslateX() < -1);
            int movesOverPage = sceneMoves.get();
            move(stage, 530);
            await(() -> eyes.getTranslateX() > 1);
            assertEquals(movesOverPage, sceneMoves.get(), "Movement inside WebView2 must not rely on JavaFX events");
            JavaFxTestRuntime.call(() -> {
                preferences.setReducedMotion(true);
                mascot.refresh();
                assertEquals(0, eyes.getTranslateX());
                assertEquals(0, pointerListeners());
                preferences.setReducedMotion(false);
                mascot.refresh();
                assertEquals(1, pointerListeners());
                preferences.setMascotEnabled(false);
                mascot.refresh();
                assertEquals(0, pointerListeners());
                preferences.setMascotEnabled(true);
                mascot.refresh();
                stage.hide();
                assertEquals(0, pointerListeners());

                return null;
            });
        } finally {
            var stopped = new CountDownLatch(1);
            JavaFxTestRuntime.call(() -> {
                mascot.dispose();
                page.shutdown(stopped::countDown);
                stage.close();
                new Robot().mouseMove(previousPointer);
                preferences.setReducedMotion(previousMotion);
                preferences.setMascotEnabled(previousMascot);
                preferences.flush();

                return null;
            });
            assertTrue(stopped.await(5, TimeUnit.SECONDS));
            server.stop(0);

            if (registerHook) {
                GlobalScreen.unregisterNativeHook();
            }
        }
    }

    private long pointerListeners() {
        try {
            var field = GlobalScreen.class.getDeclaredField("eventListeners");
            field.setAccessible(true);
            var listeners = (EventListenerList) field.get(null);

            return Arrays.stream(listeners.getListeners(NativeMouseMotionListener.class))
                    .filter(MascotPointer.class::isInstance).count();
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Could not inspect native listener cleanup", failure);
        }
    }

    private void move(Stage stage, double x) throws Exception {
        JavaFxTestRuntime.call(() -> {
            new Robot().mouseMove(stage.getX() + x, stage.getY() + 150);

            return null;
        });
    }

    private void await(BooleanSupplier condition) throws Exception {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);

        while (System.nanoTime() < deadline) {
            if (JavaFxTestRuntime.call(condition::getAsBoolean)) {
                return;
            }

            Thread.sleep(25);
        }

        throw new AssertionError("The native pointer did not reach its expected state");
    }
}
