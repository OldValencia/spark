package to.sparkapp.app.utils;

import lombok.extern.slf4j.Slf4j;
import to.sparkapp.app.config.AppPaths;

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
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.StandardOpenOption;
import javafx.application.Platform;

@Slf4j
public class SingleInstanceLock {

    private static final File PORT_FILE = new File(AppPaths.DATA_DIR, "lock.port");

    /** Sent by the running instance so an unrelated process on the same port is not mistaken for one. */
    private static final String HANDSHAKE = "SPARK-INSTANCE";
    private static final int HANDSHAKE_TIMEOUT_MS = 700;
    private static ServerSocket serverSocket;
    private static final AtomicReference<Runnable> onActivate = new AtomicReference<>();
    private static final AtomicBoolean activationPending = new AtomicBoolean();
    private static FileChannel lockChannel;
    private static FileLock processLock;

    public static void setOnActivate(Runnable callback) {
        onActivate.set(callback);
        deliverActivation();
    }

    public static synchronized boolean tryAcquire() {
        // The lock file is stable: deleting it would allow two processes to lock
        // different filesystem objects under the same pathname.
        FileChannel candidate = null;
        try {
            candidate = FileChannel.open(new File(AppPaths.DATA_DIR, "instance.lock").toPath(),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock acquired;
            try { acquired = candidate.tryLock(); }
            catch (OverlappingFileLockException alreadyHeld) { acquired = null; }
            if (acquired == null) {
                candidate.close();
                for (int attempt = 0; attempt < 20; attempt++) {
                    int port = readPort();
                    if (port > 0 && signalExistingInstance(port)) return false;
                    try { Thread.sleep(50); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
                }
                log.warn("Spark is already starting; no second instance will be created");
                return false;
            }
            lockChannel = candidate;
            processLock = acquired;
        } catch (IOException failure) {
            if (candidate != null) try { candidate.close(); } catch (IOException ignored) {}
            log.error("Could not acquire the application lock", failure);
            return false;
        }
        int existingPort = readPort();
        if (existingPort > 0) {
            if (signalExistingInstance(existingPort)) {
                try { processLock.release(); lockChannel.close(); }
                catch (IOException failure) { log.warn("Could not release the startup lock", failure); }
                processLock = null;
                lockChannel = null;
                return false; // Another instance handled the signal
            }
        }

        try {
            serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
            int port = serverSocket.getLocalPort();
            writePort(port);
            startListenerThread();
            return true;
        } catch (IOException e) {
            log.error("Could not bind socket for single instance lock", e);
            return true; // Proceed anyway if we fail to bind for some reason
        }
    }

    public static synchronized void release() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            if (processLock != null && PORT_FILE.exists()) {
                PORT_FILE.delete();
            }
            if (processLock != null) processLock.release();
            if (lockChannel != null) lockChannel.close();
            processLock = null;
            lockChannel = null;
        } catch (IOException ignored) {
        }
    }

    private static void startListenerThread() {
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

    private static void deliverActivation() {
        var callback = onActivate.get();
        if (callback != null && activationPending.getAndSet(false)) Platform.runLater(callback);
    }

    private static boolean signalExistingInstance(int port) {
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

    private static int readPort() {
        try {
            if (PORT_FILE.exists()) {
                String content = Files.readString(PORT_FILE.toPath()).trim();
                return Integer.parseInt(content);
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    private static void writePort(int port) {
        try {
            Files.writeString(PORT_FILE.toPath(), String.valueOf(port));
        } catch (Exception e) {
            log.warn("Could not write port file", e);
        }
    }
}
