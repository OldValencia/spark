package to.sparkapp.app.browser;

import java.util.function.Consumer;

import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.ptr.PointerByReference;
import lombok.extern.slf4j.Slf4j;
import lombok.Getter;

import static to.sparkapp.app.browser.ComCalls.*;

/** Owns a new-window deferral through asynchronous controller/script creation. */
@Slf4j
final class BrowserPopupRequest implements AutoCloseable {
    private final Pointer arguments;
    private final Pointer deferral;
    private final BrowserContext opener;
    private final Runnable finished;
    private final long openerNavigation;
    private BrowserContext created;
    @Getter
    private boolean closed;

    BrowserPopupRequest(Pointer arguments, BrowserContext opener, Runnable finished) {
        this.arguments = arguments;
        this.opener = opener;
        this.finished = finished;
        openerNavigation = opener.navigationId;
        var out = new PointerByReference();
        check(call(arguments, 9, out));
        deferral = out.getValue();
        call(arguments, 1);
        check(call(arguments, 6, 1));
    }

    void create(String script, Consumer<BrowserContext> prepare, Consumer<BrowserContext> activate) {
        var extended = opener.documentEvents();
        var environment = new PointerByReference();
        check(call(extended, 67, environment));
        var parent = new PointerByReference();
        check(call(opener.getController(), 21, parent));

        try (var callback = new BrowserAsyncCallback("6c4819f3-c9b7-4260-8127-c9f5bde7f68c", (status, controller) -> {
            if (cancelled()) {
                if (controller != null) {
                    call(controller, 24);
                }

                close();

                return;
            }

            try {
                check(status);
                created = new BrowserContext(controller, opener);
                created.setPresentation(false, false);
                prepare.accept(created);
                installScript(script, activate);
            } catch (Throwable failure) {
                fail(failure);
            }
        })) {
            check(call(environment.getValue(), 3, parent.getValue(), callback.pointer()));
        } finally {
            release(environment.getValue());
        }
    }

    private void installScript(String script, Consumer<BrowserContext> activate) {
        try (var callback = new BrowserAsyncCallback("b99369f3-9b11-47b5-bc6f-8e7895fcea17", (status, id) -> {
            if (cancelled()) {
                close();

                return;
            }

            try {
                check(status);
                check(call(arguments, 4, created.getCore()));
                created.loading = true;
                activate.accept(created);
                created = null;
                close();
            } catch (Throwable failure) {
                fail(failure);
            }
        })) {
            check(call(created.getCore(), 27, new WString(script), callback.pointer()));
        }
    }

    private void fail(Throwable failure) {
        log.error("Could not create an in-window browser page", failure);
        close();
    }

    private boolean cancelled() {
        return closed || opener.isClosed() || opener.navigationId != openerNavigation;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }

        closed = true;

        try {
            if (created != null) {
                created.close();
                created = null;
            }
        } finally {
            call(deferral, 3);
            release(deferral);
            release(arguments);
            finished.run();
        }
    }
}
