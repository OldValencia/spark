package to.sparkapp.app.browser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import to.sparkapp.app.utils.NativeWindowUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

@Tag("native")
class WindowsBrowserTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test @Timeout(100)
    void nativeNavigationRetainsDomAndClearsDataAcrossOrigins() throws Exception {
        assertEquals("true", System.getProperty("spark.nativeTests"));
        assertTrue(System.getenv("WEBVIEW2_USER_DATA_FOLDER").contains("native-tests"));
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body;
            if (exchange.getRequestURI().getPath().equals("/cookie")) {
                body = String.valueOf(exchange.getRequestHeaders().getFirst("Cookie"));
            } else {
                if (exchange.getRequestURI().getPath().equals("/seed")) {
                    exchange.getResponseHeaders().add("Set-Cookie", "session=secret; HttpOnly; Path=/");
                }
                body = "<!doctype html><title>Native test</title><textarea id='draft'></textarea><div style='height:2000px'></div>";
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try (var browser = new Harness()) {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            String other = "http://localhost:" + server.getAddress().getPort();
            browser.load(base + "/seed");
            browser.eval("localStorage.setItem('test','saved'); sessionStorage.setItem('test','saved'); document.querySelector('#draft').value='unsent'; window.scrollTo(0,450); window.probe('seeded')");
            assertEquals("seeded", browser.message());
            browser.run(w -> NativeWindowUtils.setVisible(w.getNativeWindowPointer(), false));
            browser.eval("window.probe(JSON.stringify({draft:document.querySelector('#draft').value,scroll:scrollY}))");
            var dom = JSON.readTree(browser.message());
            assertEquals("unsent", dom.get("draft").asText());
            assertTrue(dom.get("scroll").asInt() > 0);
            browser.eval("fetch('/cookie').then(r=>r.text()).then(window.probe)");
            assertTrue(browser.message().contains("session=secret"));
            browser.load(other + "/seed");
            browser.eval("localStorage.setItem('test','other'); sessionStorage.setItem('test','other'); window.probe('other-seeded')");
            assertEquals("other-seeded", browser.message());
            browser.eval("(()=>{const r=indexedDB.open('test-db',1);r.onupgradeneeded=()=>r.result.createObjectStore('items');r.onsuccess=()=>{r.result.close();window.probe('database-seeded')}})()");
            assertEquals("database-seeded", browser.message());
            var cleared = new CountDownLatch(1);
            var success = new AtomicReference<Boolean>();
            browser.run(w -> w.windows().clearBrowsingData(ok -> {success.set(ok); cleared.countDown();}));
            assertTrue(cleared.await(20, TimeUnit.SECONDS), "Native clear must complete");
            assertTrue(success.get());
            for (String origin : new String[]{base, other}) {
                browser.load(origin + "/check");
                browser.eval("Promise.all([fetch('/cookie').then(r=>r.text()),Promise.resolve(localStorage.getItem('test')),Promise.resolve(sessionStorage.getItem('test')),indexedDB.databases().then(d=>d.length)]).then(v=>window.probe(JSON.stringify(v)))");
                var result = JSON.readTree(browser.message());
                assertEquals("null", result.get(0).asText(), "HttpOnly cookies must be cleared across origins");
                assertTrue(result.get(1).isNull(), "Local storage must be cleared across origins");
                assertTrue(result.get(2).isNull(), "Session storage must be cleared across origins");
                assertEquals(0, result.get(3).asInt(), "IndexedDB must be cleared");
            }
            System.out.println("Native navigation, hidden DOM state and profile-wide cookie/storage clearing passed.");
        } finally { server.stop(0); }
    }

    @Test @Timeout(40)
    void nativeBindingsPreserveOriginalCallbackAndRejectWithValidJson() throws Exception {
        assertEquals("true", System.getProperty("spark.nativeTests"));
        try (var browser = new Harness()) {
            var registered = new CompletableFuture<Void>();
            browser.run(view -> {
                try {
                    view.bind("rejectProbe", args -> {
                        throw new IllegalArgumentException("Test path C:\\draft\\note \"quoted\"\nnext line");
                    });
                    assertThrows(IllegalStateException.class, () -> view.bind("probe", args -> "false"));
                    registered.complete(null);
                } catch (Throwable failure) {
                    registered.completeExceptionally(failure);
                }
            });
            registered.get(10, TimeUnit.SECONDS);
            browser.load("data:text/html,%3Ctitle%3EBridge%3C/title%3E");
            browser.eval("window.rejectProbe().then(() => window.probe('unexpected success'), error => window.probe(JSON.stringify({type:typeof error,message:error})))");
            var rejection = JSON.readTree(browser.message());
            assertEquals("string", rejection.get("type").asText());
            assertTrue(rejection.get("message").asText().contains("C:\\draft\\note \"quoted\"\nnext line"));
            browser.eval("window.probe('original callback survived').then(result => window.probe(JSON.stringify(result)))");
            assertEquals("original callback survived", browser.message());
            assertEquals("null", browser.message());
        }
    }

    static final class Harness implements AutoCloseable {
        final BlockingQueue<Boolean> loads = new LinkedBlockingQueue<>();
        final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final CountDownLatch ready = new CountDownLatch(1);
        final Thread thread;
        volatile Webview view;

        Harness() throws Exception {
            thread = new Thread(() -> {
                try {
                    view = new Webview(false);
                    NativeWindowUtils.setVisible(view.getNativeWindowPointer(), false);
                    view.bind("probe", raw -> {
                        messages.add(JSON.readTree(raw).get(0).asText());
                        return null;
                    });
                    view.windows().observe(id -> {}, (id, ok) -> loads.add(ok));
                    ready.countDown();
                    view.run();
                } catch (Throwable error) { failure.set(error); ready.countDown(); }
            }, "native-test-browser");
            thread.start();
            assertTrue(ready.await(20, TimeUnit.SECONDS));
            assertNull(failure.get(), () -> String.valueOf(failure.get()));
        }

        void run(Consumer<Webview> action) { view.dispatch(() -> action.accept(view)); }
        void eval(String script) { run(w -> w.eval(script)); }
        String message() throws Exception {
            String result = messages.poll(15, TimeUnit.SECONDS);
            assertNotNull(result, "Timed out waiting for JavaScript");
            return result;
        }
        void load(String url) throws Exception {
            loads.clear();
            long start = System.nanoTime();
            run(w -> w.loadURL(url));
            assertEquals(Boolean.TRUE, loads.poll(20, TimeUnit.SECONDS), "Navigation must complete");
            System.out.println("Local navigation completed in " + (System.nanoTime() - start) / 1_000_000 + " ms");
        }
        @Override public void close() throws Exception {
            if (view != null) view.dispatch(view::close);
            thread.join(10_000);
            assertFalse(thread.isAlive(), "Native loop must terminate");
            assertNull(failure.get(), () -> String.valueOf(failure.get()));
        }
    }
}
