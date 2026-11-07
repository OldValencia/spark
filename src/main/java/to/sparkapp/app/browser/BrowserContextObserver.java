package to.sparkapp.app.browser;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import static to.sparkapp.app.browser.ComCalls.*;

@RequiredArgsConstructor
final class BrowserContextObserver {
    private final Predicate<BrowserContext> active;
    private final Consumer<Integer> processFailure;
    @Setter
    private Consumer<Long> starting = id -> {};
    @Setter
    private BiConsumer<Long, Boolean> completed = (id, ok) -> {};
    @Setter
    private Consumer<Long> documentReady = id -> {};
    @Setter
    private Consumer<String> sourceChanged = url -> {};
    @Setter
    private Consumer<String> message = value -> {};

    void install(BrowserContext context) {
        var core = context.getCore();
        var events = context.getEvents();
        events.listen(core, 7, 8, "9adbe429-f36d-432b-9ddc-f8881fbd76e3", args -> {
            var id = new LongByReference();
            check(call(args, 9, id));
            context.navigationId = id.getValue();
            context.loading = true;
            context.presentable = false;

            if (active.test(context)) {
                starting.accept(context.navigationId);
            }
        });
        events.listen(core, 15, 16, "d33a35bf-1c49-4f98-93ab-006e0533fe1c", args -> {
            var id = new LongByReference();
            var success = new IntByReference();
            check(call(args, 5, id));
            check(call(args, 3, success));

            if (id.getValue() != context.navigationId) {
                return;
            }

            context.loading = false;
            context.successful = success.getValue() != 0;

            if (active.test(context)) {
                completed.accept(context.navigationId, context.successful);
            }
        });
        events.listen(core, 11, 12, "3c067f9f-5388-4772-8b48-79f7ef1ab37c", args -> {
            if (active.test(context)) {
                sourceChanged.accept(context.source());
            }
        });
        events.listen(core, 25, 26, "79e0aea4-990b-42d9-aa1d-0fcc2e5bc7f1", args -> {
            var kind = new IntByReference();
            check(call(args, 3, kind));

            if (kind.getValue() == 0 || active.test(context)) {
                processFailure.accept(kind.getValue());
            }
        });
        events.listen(context.documentEvents(), 64, 65, "4bac7e9c-199e-49ed-87ed-249303acf019", args -> {
            var id = new LongByReference();
            check(call(args, 3, id));

            if (id.getValue() != context.navigationId) {
                return;
            }

            context.presentable = true;

            if (active.test(context)) {
                documentReady.accept(context.navigationId);
            }
        });

        if (context.isChild()) {
            events.listen(core, 34, 35, "57213f19-00e6-49fa-8e07-898ea01ecbd2", args -> receiveMessage(context, args));
        }
    }

    void activate(BrowserContext context) {
        starting.accept(context.navigationId);

        if (!context.loading) {
            sourceChanged.accept(context.source());
            completed.accept(context.navigationId, context.successful);
        } else if (context.presentable) {
            documentReady.accept(context.navigationId);
        }
    }

    private void receiveMessage(BrowserContext context, Pointer args) {
        if (!active.test(context)) {
            return;
        }

        var value = new PointerByReference();

        if (call(args, 5, value) < 0) {
            return;
        }

        try {
            message.accept(value.getValue().getWideString(0));
        } finally {
            Ole32.INSTANCE.CoTaskMemFree(value.getValue());
        }
    }
}
