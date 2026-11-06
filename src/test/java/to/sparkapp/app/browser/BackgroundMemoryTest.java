package to.sparkapp.app.browser;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.jna.Function;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Tag("native")
class BackgroundMemoryTest {
    @Test
    @Timeout(60)
    void hiddenLowMemoryPageKeepsDraftDataAndStreamingResponse() throws Exception {
        assertEquals("true", System.getProperty("spark.nativeTests"));

        var continueStream = new CountDownLatch(1);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            if (exchange.getRequestURI().getPath().equals("/stream")) {
                exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
                exchange.sendResponseHeaders(200, 0);

                try (var body = exchange.getResponseBody()) {
                    body.write("data: first\n\n".getBytes(StandardCharsets.UTF_8));
                    body.flush();

                    if (!continueStream.await(15, TimeUnit.SECONDS)) {
                        return;
                    }

                    body.write("data: second\n\ndata: third\n\n".getBytes(StandardCharsets.UTF_8));
                    body.flush();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            } else {
                var body = "<!doctype html><textarea id='draft'></textarea><div style='height:3000px'></div>".getBytes(
                        StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            }
        });
        server.start();

        try (var browser = new WindowsBrowserTest.Harness()) {
            browser.load("http://127.0.0.1:" + server.getAddress().getPort());
            onBrowser(browser, view -> {
                view.windows().setPresentation(true, false);
                assertNativePolicy(view, true, 0);
            });
            browser.eval("""
                    document.querySelector('#draft').value='unfinished';
                    window.scrollTo(0,450);
                    window.keep=new Uint8Array(30*1024*1024);keep.fill(17);
                    window.streamText='';
                    fetch('/stream').then(async response=>{
                        const reader=response.body.getReader();const decoder=new TextDecoder();
                        let notified=false;
                        for (;;) {
                            const chunk=await reader.read();if(chunk.done)break;
                            streamText+=decoder.decode(chunk.value,{stream:true});
                            if (!notified) {notified=true;window.probe('stream-started');}
                        }
                        window.probe('stream-completed');
                    }).catch(error=>window.probe('stream-error:'+error));
                    """);
            assertEquals("stream-started", browser.message());
            onBrowser(browser, view -> {
                view.windows().setPresentation(false, true);
                assertNativePolicy(view, false, 1);
            });
            continueStream.countDown();
            // Must finish while still hidden/Low, not only after the window is restored.
            assertEquals("stream-completed", browser.message());
            browser.eval(
                    "window.probe(JSON.stringify({draft:document.querySelector('#draft').value,scroll:scrollY,bytes:keep.length,last:keep[keep.length-1],stream:streamText,hidden:document.hidden}))");

            var state = new ObjectMapper().readTree(browser.message());
            assertEquals("unfinished", state.get("draft").asText());
            assertEquals(450, state.get("scroll").asInt());
            assertEquals(30 * 1024 * 1024, state.get("bytes").asInt());
            assertEquals(17, state.get("last").asInt());
            assertTrue(state.get("stream").asText().contains("third"));
            assertTrue(state.get("hidden").asBoolean());
            onBrowser(browser, view -> {
                // Disabling memory reduction must work without showing/reloading the page.
                view.windows().setPresentation(false, false);
                assertNativePolicy(view, false, 0);
                view.windows().setPresentation(true, false);
                assertNativePolicy(view, true, 0);
            });
            browser.eval("window.probe(document.querySelector('#draft').value)");
            assertEquals("unfinished", browser.message());
            System.out.println(
                    "Hidden Low-memory streaming, draft, scroll, live JS data and native policy restoration passed.");
        } finally {
            continueStream.countDown();
            server.stop(0);
        }
    }

    private static void onBrowser(WindowsBrowserTest.Harness browser, Consumer<Webview> action) throws Exception {
        var done = new CompletableFuture<Void>();
        browser.run(view -> {
            try {
                action.accept(view);
                done.complete(null);
            } catch (Throwable failure) {
                done.completeExceptionally(failure);
            }
        });
        done.get(10, TimeUnit.SECONDS);
    }

    private static void assertNativePolicy(Webview view, boolean visible, int memoryTarget) {
        try {
            var nativeBrowser = view.windows();
            var controllerField = WindowsBrowser.class.getDeclaredField("controller");
            controllerField.setAccessible(true);

            var coreField = WindowsBrowser.class.getDeclaredField("core");
            coreField.setAccessible(true);

            var nativeVisible = new IntByReference();
            var target = new IntByReference();
            read((Pointer) controllerField.get(nativeBrowser), 3, nativeVisible);

            var memory = ComCalls.query((Pointer) coreField.get(nativeBrowser), "6921f954-79b0-437f-a997-c85811897c68");

            try {
                read(memory, 119, target);
            } finally {
                ComCalls.release(memory);
            }

            assertEquals(visible, nativeVisible.getValue() != 0);
            assertEquals(memoryTarget, target.getValue());
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static void read(Pointer object, int slot, IntByReference result) {
        var function = Function.getFunction(object.getPointer(0).getPointer((long) slot * Native.POINTER_SIZE),
                Function.ALT_CONVENTION);
        assertTrue(function.invokeInt(new Object[]{object, result}) >= 0);
    }
}
