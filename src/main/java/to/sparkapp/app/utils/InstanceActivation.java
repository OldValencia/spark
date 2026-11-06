package to.sparkapp.app.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javafx.application.Platform;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
final class InstanceActivation implements AutoCloseable {
    private static final String HANDSHAKE = "SPARK-INSTANCE";
    private static final int HANDSHAKE_TIMEOUT_MS = 700;
    private final File portFile;
    private final AtomicReference<Runnable> onActivate = new AtomicReference<>();
    private final AtomicBoolean activationPending = new AtomicBoolean();
    private ServerSocket serverSocket;

    void setOnActivate(Runnable callback) {
        onActivate.set(callback);
        deliverActivation();
    }

    void start() throws IOException {
        serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        writePort(serverSocket.getLocalPort());
        startListenerThread();
    }

    @Override
    public void close() throws IOException {
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }
    }

    private void startListenerThread() {
        var listeningSocket = serverSocket;
        var thread = new Thread(() -> {
            while (!listeningSocket.isClosed()) {
                try (var client = listeningSocket.accept()) {
                    client.getOutputStream().write((HANDSHAKE + "\n").getBytes(StandardCharsets.UTF_8));
                    client.getOutputStream().flush();

                    activationPending.set(true);
                    deliverActivation();
                } catch (IOException ignored) {
                }
            }
        }, "single-instance-listener");
        thread.setDaemon(true);
        thread.start();
    }

    private void deliverActivation() {
        var callback = onActivate.get();

        if (callback != null && activationPending.getAndSet(false)) {
            Platform.runLater(callback);
        }
    }

    boolean signalExistingInstance(int port) {
        try (var socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), HANDSHAKE_TIMEOUT_MS);
            socket.setSoTimeout(HANDSHAKE_TIMEOUT_MS);

            var reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            var greeting = reader.readLine();

            if (!HANDSHAKE.equals(greeting)) {
                log.warn("Port {} is taken by another application, starting anyway", port);

                return false;
            }

            log.info("Another instance is running — signalled it to come to front");

            return true;
        } catch (IOException e) {
            return false;
        }
    }

    int readPort() {
        try {
            if (portFile.exists()) {
                String content = Files.readString(portFile.toPath()).trim();

                return Integer.parseInt(content);
            }
        } catch (Exception ignored) {
        }

        return -1;
    }

    private void writePort(int port) {
        try {
            Files.writeString(portFile.toPath(), String.valueOf(port));
        } catch (Exception e) {
            log.warn("Could not write port file", e);
        }
    }
}
