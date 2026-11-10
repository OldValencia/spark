package to.sparkapp.app.browser;

import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.Structure.ByValue;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.platform.win32.WinDef.RECT;
import com.sun.jna.ptr.PointerByReference;
import lombok.Getter;

import static to.sparkapp.app.browser.ComCalls.*;

/** A top-level browsing context. The root controller is borrowed from webview. */
@Getter
final class BrowserContext implements AutoCloseable {
    public static class Bounds extends RECT implements ByValue {
    }
    private static final int CORE_RELOAD = 31;
    private final Pointer controller;
    private final Pointer core;
    private final boolean child;
    private final BrowserContext parent;
    private final BrowserComEvents events = new BrowserComEvents();
    private final BrowserMemoryPolicy memoryPolicy;
    private final BrowserAudio audio;
    private final BrowserMobileView mobileView;
    private Pointer documentEvents;
    private boolean closed;
    long navigationId;
    boolean loading;
    boolean presentable;
    boolean successful = true;
    private Boolean visible;

    BrowserContext(Pointer controller, BrowserContext parent) {
        if (controller == null) {
            throw new IllegalStateException("WebView2 controller is unavailable");
        }

        this.controller = controller;
        this.parent = parent;
        child = parent != null;

        if (child) {
            call(controller, 1);
        }

        var out = new PointerByReference();
        check(call(controller, 25, out));
        core = out.getValue();
        memoryPolicy = new BrowserMemoryPolicy(core);
        audio = new BrowserAudio(core);
        mobileView = new BrowserMobileView(core);
    }

    void setMuted(boolean muted) {
        if (!closed) {
            audio.setMuted(muted);
        }
    }

    void setMobile(boolean mobile) {
        if (!closed) {
            mobileView.setMobile(mobile);
        }
    }

    void reload() {
        check(call(core, CORE_RELOAD));
    }

    Pointer documentEvents() {
        if (documentEvents == null) {
            documentEvents = query(core, "9e8f0cf8-e670-4b5e-b2bc-73e061e3184c");
        }

        return documentEvents;
    }

    String source() {
        var out = new PointerByReference();
        check(call(core, 4, out));

        try {
            return out.getValue().getWideString(0);
        } finally {
            Ole32.INSTANCE.CoTaskMemFree(out.getValue());
        }
    }

    void navigate(String url) {
        check(call(core, 5, new WString(url)));
    }

    void evaluate(String script) {
        check(call(core, 29, new WString(script), null));
    }

    boolean focus() {
        var result = call(controller, 12, 0);

        if (result == 0x80070057) {
            return false;
        }

        check(result);

        return true;
    }

    void resizeTo(BrowserContext root) {
        if (child && !closed) {
            var bounds = new RECT();
            check(call(root.controller, 5, bounds));
            var value = new Bounds();
            value.left = bounds.left;
            value.top = bounds.top;
            value.right = bounds.right;
            value.bottom = bounds.bottom;
            check(call(controller, 6, value));
        }
    }

    void setPresentation(boolean shown, boolean lowMemory) {
        if (closed) {
            return;
        }

        if (shown) {
            memoryPolicy.setTarget(0);
        }

        if (visible == null || visible != shown) {
            check(call(controller, 4, shown ? 1 : 0));
            visible = shown;
        }

        if (!shown) {
            memoryPolicy.setTarget(lowMemory ? 1 : 0);
        }
    }

    void close(boolean processExited) {
        if (closed) {
            return;
        }

        closed = true;

        try {
            events.detach(processExited);

            if (!processExited) {
                check(call(controller, 24));
            }
        } finally {
            release(documentEvents);
            memoryPolicy.close();
            audio.close();
            release(core);

            if (child) {
                release(controller);
            }
        }
    }

    @Override
    public void close() {
        close(false);
    }
}
