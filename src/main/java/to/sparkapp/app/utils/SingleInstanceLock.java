package to.sparkapp.app.utils;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.StandardOpenOption;

import lombok.extern.slf4j.Slf4j;
import to.sparkapp.app.config.AppPaths;

@Slf4j
public class SingleInstanceLock {

    private static final File PORT_FILE = new File(AppPaths.DATA_DIR, "lock.port");

    private static final InstanceActivation activation = new InstanceActivation(PORT_FILE);
    private static FileChannel lockChannel;
    private static FileLock processLock;

    public static void setOnActivate(Runnable callback) {
        activation.setOnActivate(callback);
    }

    public static synchronized boolean tryAcquire() {
        // The lock file is stable: deleting it would allow two processes to lock
        // different filesystem objects under the same pathname.
        FileChannel candidate = null;

        try {
            candidate = FileChannel.open(new File(AppPaths.DATA_DIR, "instance.lock").toPath(),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);

            FileLock acquired;

            try {
                acquired = candidate.tryLock();
            } catch (OverlappingFileLockException alreadyHeld) {
                acquired = null;
            }

            if (acquired == null) {
                candidate.close();

                for (int attempt = 0; attempt < 20; attempt++) {
                    int port = activation.readPort();

                    if (port > 0 && activation.signalExistingInstance(port)) {
                        return false;
                    }

                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

                log.warn("Spark is already starting; no second instance will be created");

                return false;
            }

            lockChannel = candidate;
            processLock = acquired;
        } catch (IOException failure) {
            if (candidate != null) {
                try {
                    candidate.close();
                } catch (IOException ignored) {
                }
            }

            log.error("Could not acquire the application lock", failure);

            return false;
        }

        int existingPort = activation.readPort();

        if (existingPort > 0) {
            if (activation.signalExistingInstance(existingPort)) {
                try {
                    processLock.release();
                    lockChannel.close();
                } catch (IOException failure) {
                    log.warn("Could not release the startup lock", failure);
                }

                processLock = null;
                lockChannel = null;

                return false; // Another instance handled the signal
            }
        }

        try {
            activation.start();

            return true;
        } catch (IOException e) {
            log.error("Could not bind socket for single instance lock", e);

            return true; // Proceed anyway if we fail to bind for some reason
        }
    }

    public static synchronized void release() {
        try {
            activation.close();

            if (processLock != null && PORT_FILE.exists()) {
                PORT_FILE.delete();
            }

            if (processLock != null) {
                processLock.release();
            }

            if (lockChannel != null) {
                lockChannel.close();
            }

            processLock = null;
            lockChannel = null;
        } catch (IOException ignored) {
        }
    }

}
