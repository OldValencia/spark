package to.sparkapp.app.ui.webview;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.jna.Function;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import to.sparkapp.app.browser.WebviewManager;
import to.sparkapp.app.config.AiConfiguration.AiConfig;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.support.JavaFxTestRuntime;
import to.sparkapp.app.utils.SingleInstanceLock;

@Tag("native")
class FxWebViewPaneTest {
    @Test
    @Timeout(120)
    void liveSitesSurviveVisibilityChangesAndRecoverFromProcessFailures() throws Exception {
        assertEquals("true", System.getProperty("spark.nativeTests"));

        JavaFxTestRuntime.start();

        var hits = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            if (!exchange.getRequestURI().getPath().equals("/favicon.ico")) {
                hits.incrementAndGet();
            }

            if (exchange.getRequestURI().getPath().equals("/slow")) {
                try {
                    Thread.sleep(250);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }

            var bytes = "<!doctype html><title>UI test</title><textarea id='draft'></textarea><div style='height:3000px'></div>".getBytes(
                    StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        var preferences = new AppPreferences();
        preferences.setRememberLastAi(true);

        var first = new AiConfig("native-first", "First", base + "/first", null, null);
        var second = new AiConfig("native-second", "Second",
                "http://localhost:" + server.getAddress().getPort() + "/second", null, null);
        var stage = new AtomicReference<Stage>();
        var pane = new AtomicReference<FxWebViewPane>();
        long coldStart = System.nanoTime();

        try {
            fx(() -> {
                var window = new Stage();
                var content = new FxWebViewPane(first.url(), preferences);
                window.setScene(new Scene(content, 700, 580));
                window.setX(-20000);
                window.setY(-20000);
                content.setCurrentConfig(first, null);
                stage.set(window);
                pane.set(content);
                window.show();

                return null;
            });
            await(() -> !active(pane.get()).isLoading());
            System.out.println(
                    "Cold native host to completed local page: " + (System.nanoTime() - coldStart) / 1_000_000 + " ms");
            sampleMemory("one loaded site");

            int initialRequests = hits.get();
            active(pane.get()).eval(
                    "document.querySelector('#draft').value='unsent';window.scrollTo(0,420);window.testCounter=0;window.testInterval=setInterval(()=>window.testCounter++,50);fetch('/slow').then(r=>r.text()).then(()=>window.requestCompleted=true)");
            fx(() -> {
                pane.get().onWindowHidden();
                stage.get().hide();

                return null;
            });
            Thread.sleep(350);
            active(pane.get()).eval("window.hiddenDuringPause=document.hidden");

            long warm = System.nanoTime();
            fx(() -> {
                stage.get().show();
                pane.get().onWindowRestored();

                return null;
            });
            System.out.println("Warm show/restore call: " + (System.nanoTime() - warm) / 1_000_000 + " ms");
            fx(() -> {
                pane.get().setCurrentConfig(second, null);

                return null;
            });
            await(() -> !active(pane.get()).isLoading());
            active(pane.get()).eval("document.querySelector('#draft').value='second-unsent'");
            sampleMemory("two cached sites");

            int afterSecond = hits.get();
            assertTrue(afterSecond > initialRequests);
            fx(() -> {
                pane.get().setCurrentConfig(first, null);

                return null;
            });

            for (int i = 0; i < 12; i++) {
                fx(() -> {
                    pane.get().onWindowHidden();
                    stage.get().hide();
                    stage.get().show();
                    pane.get().onWindowRestored();

                    return null;
                });
            }

            active(pane.get()).eval(
                    "clearInterval(window.testInterval);history.replaceState({},'', '/first?draft='+document.querySelector('#draft').value+'&scroll='+scrollY+'&ticks='+window.testCounter+'&hidden='+window.hiddenDuringPause+'&network='+Boolean(window.requestCompleted))");
            await(() -> preferences.getLastUrl() != null && preferences.getLastUrl().contains("draft=unsent"));
            assertTrue(preferences.getLastUrl().contains("scroll=420"));
            assertFalse(preferences.getLastUrl().contains("ticks=0&"));
            assertTrue(preferences.getLastUrl().contains("hidden=true"), "WebView2 must know the page is hidden");
            assertTrue(preferences.getLastUrl().endsWith("network=true"), "An in-flight request must survive hiding");
            assertEquals(afterSecond, hits.get(), "Cached switching and restore must not issue another page request");
            assertTrue(preferences.getSiteUrl(second).endsWith("/second"));
            // Crash only this test profile's active renderer. The other origin must
            // retain its own live page; recovery is explicit, never an automatic refresh.

            var crashed = active(pane.get());
            crashed.dispatch(() -> crashed.loadURL("edge://crash"));
            await(() -> crashed.getLoadError() != null && crashed.getLoadError().contains("stopped unexpectedly"));
            assertFalse(crashed.isLoading());
            fx(() -> {
                pane.get().setCurrentConfig(second, null);

                return null;
            });
            assertNull(active(pane.get()).getLoadError());
            active(pane.get()).eval(
                    "history.replaceState({},'', '/second?draft='+document.querySelector('#draft').value)");
            await(() -> preferences.getSiteUrl(second).contains("draft=second-unsent"));
            fx(() -> {
                pane.get().setCurrentConfig(first, null);
                retry(pane.get());

                return null;
            });
            await(() -> !crashed.isLoading() && crashed.getLoadError() == null);
            assertEquals(afterSecond + 1, hits.get(), "Only the explicitly retried page should reload");
            System.out.println(
                    "Renderer crash detected; retry recovered its page and preserved the other site's draft.");

            long failedPid = browserProcessId(crashed);
            assertTrue(ProcessHandle.current().descendants().anyMatch(process -> process.pid() == failedPid),
                    "Only the isolated test worker's own browser process may be terminated");
            assertTrue(ProcessHandle.of(failedPid).orElseThrow().destroyForcibly());
            await(() -> crashed.getLoadError() != null && crashed.getLoadError().contains("browser process stopped"));
            assertEquals(afterSecond + 1, hits.get(), "Browser failure must not silently reload sites");
            fx(() -> {
                retry(pane.get());

                return null;
            });
            await(() -> !crashed.isLoading() && crashed.getLoadError() == null);
            assertNotEquals(failedPid, browserProcessId(crashed), "Retry must recreate the failed browser connection");
            fx(() -> {
                pane.get().setCurrentConfig(second, null);

                return null;
            });
            assertNotNull(active(pane.get()).getLoadError(), "Other affected sites must wait for explicit recovery");
            fx(() -> {
                retry(pane.get());

                return null;
            });
            await(() -> !active(pane.get()).isLoading() && active(pane.get()).getLoadError() == null);
            fx(() -> {
                pane.get().setCurrentConfig(first, null);

                return null;
            });
            System.out.println(
                    "Browser-process failure detected; explicit retries recreated affected views without automatic site reloads.");

            var failureSite = new AiConfig("native-failure", "Unavailable", "http://127.0.0.1:1/unavailable", null,
                    null);
            fx(() -> {
                pane.get().setCurrentConfig(failureSite, null);

                return null;
            });
            await(() -> active(pane.get()).getLoadError() != null);
            assertFalse(active(pane.get()).isLoading());
            fx(() -> {
                pane.get().setCurrentConfig(first, null);

                return null;
            });
            fx(() -> {
                pane.get().reconcileSites(List.of());
                pane.get().setCurrentConfig(first, null);

                return null;
            });
            await(() -> !active(pane.get()).isLoading());

            var activated = new CountDownLatch(1);
            SingleInstanceLock.setOnActivate(activated::countDown);
            assertTrue(SingleInstanceLock.tryAcquire());
            assertFalse(SingleInstanceLock.tryAcquire(), "A second launch must activate the first instance");
            assertTrue(activated.await(3, TimeUnit.SECONDS));
            SingleInstanceLock.release();

            var closed = new CountDownLatch(1);
            fx(() -> {
                pane.get().shutdown(closed::countDown);
                stage.get().hide();

                return null;
            });
            assertTrue(closed.await(15, TimeUnit.SECONDS));
            // A browser stopped during startup must still complete its lifecycle.

            for (int i = 0; i < 3; i++) {
                var browser = new WebviewManager(preferences);
                browser.hibernate();
                browser.init(base, 0, 0, 0, 100, 100);

                var stopped = new CountDownLatch(2);
                browser.shutdown(stopped::countDown);
                browser.shutdown(stopped::countDown);
                assertTrue(stopped.await(15, TimeUnit.SECONDS));
            }

            System.out.println(
                    "DOM, scroll, background activity, independent site URLs, 12 rapid restores and startup shutdown passed.");
        } finally {
            if (stage.get() != null) {
                fx(() -> {
                    stage.get().hide();

                    return null;
                });
            }

            SingleInstanceLock.release();
            preferences.flush();
            server.stop(0);
        }
    }

    private static void sampleMemory(String label) throws Exception {
        String children = ProcessHandle.current().descendants().map(p -> Long.toString(p.pid()))
                .collect(Collectors.joining(","));
        String ids = ProcessHandle.current().pid() + (children.isEmpty() ? "" : "," + children);
        var process = new ProcessBuilder("powershell", "-NoProfile", "-Command", "(Get-Process -Id " + ids +
                " -ErrorAction SilentlyContinue | Measure-Object WorkingSet64 -Sum).Sum").redirectErrorStream(true)
                .start();
        assertTrue(process.waitFor(10, TimeUnit.SECONDS));

        String bytes = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        System.out.println("Process-tree summed working set (JVM + WebView2), " + label + ": " +
                Long.parseLong(bytes) / 1024 / 1024 + " MiB (shared pages may be counted more than once)");
    }

    private static WebviewManager active(FxWebViewPane pane) {
        try {
            var field = FxWebViewPane.class.getDeclaredField("bridge");
            field.setAccessible(true);

            return (WebviewManager) field.get(pane);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static void retry(FxWebViewPane pane) {
        pane.lookupAll(".button").stream()
                .filter(node -> node instanceof Button button && button.getText().equals("Retry") && button.isVisible())
                .map(node -> (Button) node).findFirst().orElseThrow().fire();
    }

    private static long browserProcessId(WebviewManager manager) throws Exception {
        var result = new CompletableFuture<Long>();
        manager.dispatch(() -> {
            try {
                var viewField = WebviewManager.class.getDeclaredField("webview");
                viewField.setAccessible(true);

                var view = viewField.get(manager);
                var windowsMethod = view.getClass().getDeclaredMethod("windows");
                windowsMethod.setAccessible(true);

                var windows = windowsMethod.invoke(view);
                var coreField = windows.getClass().getDeclaredField("core");
                coreField.setAccessible(true);

                var core = (Pointer) coreField.get(windows);
                var pid = new IntByReference();
                var function = Function.getFunction(core.getPointer(0).getPointer(37L * Native.POINTER_SIZE),
                        Function.ALT_CONVENTION);
                assertTrue(function.invokeInt(new Object[]{core, pid}) >= 0);
                result.complete(Integer.toUnsignedLong(pid.getValue()));
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            }
        });

        return result.get(10, TimeUnit.SECONDS);
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        var task = new FutureTask<>(action);
        Platform.runLater(task);

        return task.get(10, TimeUnit.SECONDS);
    }

    private static void await(BooleanSupplier condition) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (!condition.getAsBoolean() && System.nanoTime() < until) {
            Thread.sleep(20);
        }
        assertTrue(condition.getAsBoolean(), "Timed out waiting for browser state");
    }
}
