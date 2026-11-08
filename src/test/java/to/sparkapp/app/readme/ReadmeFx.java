package to.sparkapp.app.readme;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;

/** Runs work on the JavaFX thread of the Spark instance being captured. */
final class ReadmeFx {
    private ReadmeFx() {
    }

    static <T> T call(Callable<T> action) throws Exception {
        var task = new FutureTask<>(action);
        Platform.runLater(task);

        return task.get(20, TimeUnit.SECONDS);
    }

    static void run(Runnable action) throws Exception {
        call(() -> {
            action.run();
            return null;
        });
    }

    /** Waits until Spark's JavaFX toolkit accepts work. */
    static void awaitToolkit() throws Exception {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (System.nanoTime() < deadline) {
            try {
                call(() -> null);
                return;
            } catch (IllegalStateException notStarted) {
                pause(100);
            }
        }
        throw new IllegalStateException("Spark did not start");
    }

    static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }
}
