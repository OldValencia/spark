package to.sparkapp.app.browser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import lombok.Getter;
import lombok.Setter;

import static to.sparkapp.app.browser.ComCalls.*;

/** Owns related top-level contexts sharing one native viewport. */
final class BrowserContextStack {
    private final BrowserContext root;
    private final Consumer<Runnable> defer;
    private final Consumer<BrowserContext> changed;
    private final List<BrowserContext> contexts = new ArrayList<>();
    private final Set<BrowserPopupRequest> pending = new HashSet<>();
    private final BrowserPageZoom zoom = new BrowserPageZoom();
    @Getter
    private BrowserContext active;
    private String script;
    private Consumer<BrowserContext> prepare;
    private boolean visible;
    private boolean lowMemory;
    private boolean muted;
    private boolean mobile;
    private boolean closed;
    @Setter
    private boolean reduceInactive = true;

    BrowserContextStack(BrowserContext root, Consumer<Runnable> defer, Consumer<BrowserContext> changed) {
        this.root = root;
        this.defer = defer;
        this.changed = changed;
        active = root;
        contexts.add(root);
        zoom.prepare(root);
    }

    void enable(String script, Consumer<BrowserContext> prepare) {
        this.script = script;
        this.prepare = prepare;
        installPopupEvents(root);
    }

    private void installPopupEvents(BrowserContext context) {
        context.getEvents().listen(context.getCore(), 44, 45, "d4c185fe-c81c-4989-97af-2d3fa7ab5651",
                args -> open(context, args));
        context.getEvents().listen(context.getCore(), 59, 60, "5c19e9e0-092f-486b-affa-ca8231913039",
                args -> defer.accept(() -> closePage(context)));
    }

    private void open(BrowserContext opener, Pointer arguments) {
        var request = new BrowserPopupRequest(arguments, opener, () -> pending.removeIf(BrowserPopupRequest::isClosed));
        pending.add(request);

        try {
            request.create(script, context -> {
                zoom.prepare(context);
                context.setMuted(muted);
                context.setMobile(mobile);
                prepare.accept(context);
                installPopupEvents(context);
            }, context -> {
                contexts.add(context);
                activate(context);
            });
        } catch (Throwable failure) {
            request.close();
            throw failure;
        }
    }

    private void activate(BrowserContext context) {
        active = context;
        // A page opened by the site does not keep a zoom set before it was attached; set it again.
        zoom.apply(context);
        setPresentation(visible, lowMemory);
        changed.accept(context);
    }

    void setPresentation(boolean visible, boolean lowMemory) {
        this.visible = visible;
        this.lowMemory = lowMemory;

        for (var context : contexts) {
            context.resizeTo(root);
            context.setPresentation(visible && context == active, context == active ? lowMemory : reduceInactive);
        }
    }

    void setZoomFactor(double factor) {
        zoom.setFactor(factor, contexts);
    }

    /** Mutes the site's main page and every page it opened, including pages it opens later. */
    void setMuted(boolean muted) {
        this.muted = muted;
        contexts.forEach(context -> context.setMuted(muted));
    }

    /** Shows the site's main page and every page it opened as on a phone, including pages it opens later. */
    void setMobile(boolean mobile) {
        this.mobile = mobile;
        contexts.forEach(context -> context.setMobile(mobile));
    }

    void back() {
        var canGoBack = new IntByReference();
        check(call(active.getCore(), 38, canGoBack));

        if (canGoBack.getValue() != 0) {
            check(call(active.getCore(), 40));
        } else {
            closePage(active);
        }
    }

    void closePage(BrowserContext context) {
        if (context == root || context.isClosed()) {
            return;
        }

        contexts.remove(context);
        context.close();

        if (context == active) {
            var parent = context.getParent();

            while (parent.isClosed()) {
                parent = parent.getParent();
            }

            activate(parent);
        }
    }

    void returnToRoot() {
        for (var request : List.copyOf(pending)) {
            request.close();
        }

        for (var context : List.copyOf(contexts).reversed()) {
            closePage(context);
        }
    }

    void close(boolean processExited) {
        if (closed) {
            return;
        }

        closed = true;

        for (var request : List.copyOf(pending)) {
            request.close();
        }

        for (var context : List.copyOf(contexts).reversed()) {
            context.close(processExited);
        }

        contexts.clear();
    }
}
