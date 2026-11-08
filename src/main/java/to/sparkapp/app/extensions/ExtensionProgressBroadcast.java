package to.sparkapp.app.extensions;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

/** Delivers the current extension work stage to listeners; a new listener receives the latest stage at once. */
@Slf4j
final class ExtensionProgressBroadcast {
    private final CopyOnWriteArrayList<Consumer<ExtensionProgress>> listeners = new CopyOnWriteArrayList<>();
    private volatile ExtensionProgress current = new ExtensionProgress("Waiting for extension synchronization…");

    void add(Consumer<ExtensionProgress> listener) {
        listeners.add(listener);
        listener.accept(current);
    }

    void remove(Consumer<ExtensionProgress> listener) {
        listeners.remove(listener);
    }

    void report(String message) {
        current = new ExtensionProgress(message);
        for (var listener : listeners) {
            try {
                listener.accept(current);
            } catch (RuntimeException error) {
                log.warn("Extension progress listener failed", error);
            }
        }
    }
}
