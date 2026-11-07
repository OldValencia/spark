package to.sparkapp.app.browser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Tag("native")
@Timeout(60)
class BrowserCacheTest {
    @TempDir
    Path directory;

    @Test
    void cacheableAssetsSurviveAFullJvmRestartWhileUncacheableHtmlIsFetchedAgain() throws Exception {
        var scripts = new AtomicInteger();
        var documents = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            boolean script = exchange.getRequestURI().getPath().equals("/asset.js");
            var body = script ? "window.cacheMarker='cached-script';" :
                    "<!doctype html><script src='/asset.js'></script><title>Cache probe</title>";

            if (script) {
                scripts.incrementAndGet();
            } else if (exchange.getRequestURI().getPath().equals("/page")) {
                documents.incrementAndGet();
            }

            exchange.getResponseHeaders().set("Content-Type", script ? "text/javascript" : "text/html");
            exchange.getResponseHeaders().set("Cache-Control", script ? "public, max-age=3600" : "no-store");
            var bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        try {
            var url = "http://127.0.0.1:" + server.getAddress().getPort() + "/page";
            visit(url, "first");
            assertEquals(1, scripts.get());
            visit(url, "second");
            assertEquals(2, documents.get(), "The no-store document must be requested again");
            assertEquals(1, scripts.get(), "A fresh JVM must reuse the disk-cached script");
            System.out.println("Two fresh JVMs: 2 document requests, 1 script request; disk cache reused.");
        } finally {
            server.stop(0);
        }
    }

    private void visit(String url, String name) throws Exception {
        var log = directory.resolve(name + ".log");
        var command = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                "-Dspark.nativeTests=true", "-cp", System.getProperty("spark.testRuntimeClasspath"),
                BrowserCacheProbe.class.getName(), url);
        command.environment().put("WEBVIEW2_USER_DATA_FOLDER", profile.toString());
        command.redirectErrorStream(true).redirectOutput(log.toFile());
        var process = command.start();
        var descendants = new HashSet<ProcessHandle>();

        try {
            var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(25);

            while (process.isAlive() && System.nanoTime() < deadline) {
                process.descendants().forEach(descendants::add);
                process.waitFor(25, TimeUnit.MILLISECONDS);
            }

            assertTrue(process.waitFor(25, TimeUnit.SECONDS), "The cache probe must exit");
            assertTrue(!descendants.isEmpty(), "The probe must observe its browser processes");
            var output = Files.readString(log);
            assertEquals(0, process.exitValue(), output);
            assertTrue(output.contains("CACHE_VISIT_COMPLETE"), output);

            for (var child : descendants) {
                if (child.isAlive()) {
                    child.onExit().get(10, TimeUnit.SECONDS);
                }
            }

            assertTrue(descendants.stream().noneMatch(ProcessHandle::isAlive), "No browser process may bridge visits");
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private final Path profile = Path.of(System.getenv("WEBVIEW2_USER_DATA_FOLDER"))
            .resolveSibling("cache-" + UUID.randomUUID());
}
