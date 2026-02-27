package to.sparkapp.app.utils;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class SingleInstanceLock {

    private static final int PORT = 0xFFFA;

    private static ServerSocket serverSocket;
    private static final AtomicReference<Runnable> onActivate = new AtomicReference<>();

    public static void setOnActivate(Runnable callback) {
        onActivate.set(callback);
    }

    public static boolean tryAcquire() {
        try {
            serverSocket = new ServerSocket(PORT, 1, InetAddress.getLoopbackAddress());
            startListenerThread();
            return true;
        } catch (IOException e) {
            signalExistingInstance();
            return false;
        }
    }

    public static void release() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
        }
    }

    private static void startListenerThread() {
        var thread = new Thread(() -> {
            while (!serverSocket.isClosed()) {
                try {
                    var client = serverSocket.accept();
                    client.close();
                    var callback = onActivate.get();
                    if (callback != null) {
                        javafx.application.Platform.runLater(callback);
                    }
                } catch (IOException ignored) {
                }
            }
        }, "single-instance-listener");
        thread.setDaemon(true);
        thread.start();
    }

    private static void signalExistingInstance() {
        try (var ignored = new Socket(InetAddress.getLoopbackAddress(), PORT)) {
            log.info("Another instance is running — signalled it to come to front");
        } catch (IOException e) {
            log.warn("Could not signal existing instance", e);
        }
    }
}
