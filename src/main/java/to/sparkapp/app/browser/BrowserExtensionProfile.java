package to.sparkapp.app.browser;

import static to.sparkapp.app.browser.ComCalls.call;
import static to.sparkapp.app.browser.ComCalls.check;
import static to.sparkapp.app.browser.ComCalls.query;
import static to.sparkapp.app.browser.ComCalls.release;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Resolves the profile's extension interface for one operation and releases it afterwards. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class BrowserExtensionProfile {
    private static final String CORE13 = "f75f09a8-667e-4983-88d6-c8773f315e84";
    private static final int CORE13_GET_PROFILE = 105;
    private static final String PROFILE7 = "7b4c7906-a1aa-4cb4-b723-db09f813d541";

    /** Failures complete the given result exceptionally instead of being thrown. */
    static void use(Pointer core, Consumer<Pointer> operation, CompletableFuture<?> result) {
        Pointer core13 = null;
        Pointer profile = null;
        Pointer extensions = null;
        try {
            core13 = query(core, CORE13);
            var out = new PointerByReference();
            check(call(core13, CORE13_GET_PROFILE, out));
            profile = out.getValue();
            extensions = query(profile, PROFILE7);
            operation.accept(extensions);
        } catch (Exception error) {
            result.completeExceptionally(error);
        } finally {
            release(extensions);
            release(profile);
            release(core13);
        }
    }
}
