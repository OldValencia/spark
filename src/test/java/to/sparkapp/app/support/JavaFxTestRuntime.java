package to.sparkapp.app.support;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;

public final class JavaFxTestRuntime {
    private static final CompletableFuture<Void> READY = new CompletableFuture<>();

    private JavaFxTestRuntime() {
    }

    static {
        Platform.startup(() -> {
            Platform.setImplicitExit(false);
            READY.complete(null);
        });
    }

    public static void start() throws Exception {
        READY.get(10, TimeUnit.SECONDS);
    }

    public static <T> T call(Callable<T> action) throws Exception {
        start();

        var task = new FutureTask<>(action);
        Platform.runLater(task);

        return task.get(10, TimeUnit.SECONDS);
    }
}
