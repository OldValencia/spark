package to.sparkapp.app.browser;

import static to.sparkapp.app.browser.ComCalls.*;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Guid.GUID;
import com.sun.jna.ptr.PointerByReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
final class BrowserMemoryPolicy implements AutoCloseable {
    private final Pointer core;
    private Pointer memoryController;
    private boolean memoryPolicyChecked;
    private Integer memoryTarget;

    void setTarget(int target) {
        if (!memoryPolicyChecked) {
            memoryPolicyChecked = true;

            var out = new PointerByReference();
            int result = call(core, 0, new GUID("6921f954-79b0-437f-a997-c85811897c68"), out);

            if (result >= 0) {
                memoryController = out.getValue();
            } else if (result == 0x80004002) {
                log.debug("This WebView2 Runtime does not support memory targets");
            } else {
                log.warn("Memory target interface unavailable: 0x{}", Integer.toHexString(result));
            }
        }

        if (memoryController == null || (memoryTarget != null && memoryTarget == target)) {
            return;
        }

        int result = call(memoryController, 120, target);

        if (result >= 0) {
            memoryTarget = target;
        } else {
            // Resource optimization must not make an otherwise usable page inaccessible.
            log.warn("Disabling browser memory targets after error 0x{}", Integer.toHexString(result));
            release(memoryController);
            memoryController = null;
        }
    }

    @Override
    public void close() {
        release(memoryController);
        memoryController = null;
    }
}
