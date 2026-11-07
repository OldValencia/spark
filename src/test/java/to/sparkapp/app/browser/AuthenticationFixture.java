package to.sparkapp.app.browser;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;

final class AuthenticationFixture implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    final AtomicReference<String> submitted = new AtomicReference<>();
    final String site;
    final String identity;

    AuthenticationFixture() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        site = "http://127.0.0.1:" + server.getAddress().getPort();
        identity = "http://localhost:" + server.getAddress().getPort();
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            var path = exchange.getRequestURI().getPath();
            String body;

            if (path.equals("/logowanie")) {
                submitted.set(exchange.getRequestMethod() + ":" +
                        new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                exchange.getResponseHeaders().set("Location", site + "/custom-return?nieznany=temporary");
                exchange.sendResponseHeaders(302, -1);
                exchange.close();

                return;
            } else if (path.equals("/popup")) {
                body = "<script>location.href='" + site + "/popup-complete';</script>";
            } else if (path.equals("/popup-complete")) {
                var cookie = String.valueOf(exchange.getRequestHeaders().getFirst("Cookie"));
                body = "<script>window.opener.postMessage('popup:" + cookie.contains("session=shared") +
                        "','" + site + "');window.close();</script>";
            } else {
                exchange.getResponseHeaders().add("Set-Cookie", "session=shared; HttpOnly; SameSite=Lax; Path=/");
                body = """
                        <!doctype html><title>Local authentication fixture</title>
                        <textarea id="draft">unsent</textarea>
                        <a id="separate" rel="opener" target="_blank" href="%s/popup">Sign in</a>
                        <form id="login" method="post" action="%s/logowanie">
                          <input name="nonce" value="local-fixture">
                        </form>
                        <script>
                        addEventListener('message', event => {
                            if(event.origin === '%s') window.probe(event.data+':'+draft.value);
                        });
                        </script>
                        """.formatted(identity, identity, site);
            }

            var bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @Override
    public void close() {
        server.stop(0);
        executor.close();
    }
}
