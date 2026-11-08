package to.sparkapp.app.browser;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.jna.Function;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Guid.GUID;
import com.sun.jna.ptr.PointerByReference;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

@EnabledOnOs(OS.WINDOWS)
class BrowserStatusCallbackTest {
    private static final String IID = BrowserProfileData.CLEAR_BROWSING_DATA_COMPLETED;

    @Test
    void nativeReferenceSurvivesSubmissionAndCompletionUntilNativeRelease() {
        int baseline = BrowserStatusCallback.retainedCount();
        var result = new AtomicReference<Boolean>();

        try (var callback = new BrowserStatusCallback(IID, result::set)) {
            var pointer = callback.pointer();
            assertEquals(2, call(pointer, 1));

            try {
                callback.close();
                assertEquals(baseline + 1, BrowserStatusCallback.retainedCount());
                assertEquals(0, call(pointer, 3, 0));
                assertEquals(Boolean.TRUE, result.get());
                assertEquals(baseline + 1, BrowserStatusCallback.retainedCount());
            } finally {
                assertEquals(0, call(pointer, 2));
            }
        }

        assertEquals(baseline, BrowserStatusCallback.retainedCount());
    }

    @Test
    void failedSubmissionWithoutNativeReferenceDoesNotRetainCallback() {
        int baseline = BrowserStatusCallback.retainedCount();

        try (var callback = new BrowserStatusCallback(IID, ok -> fail("No callback expected"))) {
            assertEquals(baseline + 1, BrowserStatusCallback.retainedCount());
        }

        assertEquals(baseline, BrowserStatusCallback.retainedCount());
    }

    @Test
    void queryInterfaceAddsAReferenceAndClearsUnsupportedOutput() {
        int baseline = BrowserStatusCallback.retainedCount();

        try (var callback = new BrowserStatusCallback(IID, ok -> {
        })) {
            var out = new PointerByReference();
            var iid = new GUID(IID);
            assertEquals(0, call(callback.pointer(), 0, iid, out));

            try {
                assertEquals(callback.pointer(), out.getValue());
            } finally {
                assertEquals(1, call(out.getValue(), 2));
            }

            var unknown = new GUID("00000000-0000-0000-C000-000000000046");
            assertEquals(0, call(callback.pointer(), 0, unknown, out));
            assertEquals(1, call(out.getValue(), 2));

            var unsupported = new GUID("00000000-0000-0000-0000-000000000000");
            assertEquals(0x80004002, call(callback.pointer(), 0, unsupported, out));
            assertNull(out.getValue());
        }

        assertEquals(baseline, BrowserStatusCallback.retainedCount());
    }

    @Test
    void reportsFailureOnceAndKeepsExceptionsInsideNativeBoundary() {
        var calls = new AtomicInteger();
        var result = new AtomicReference<Boolean>();

        try (var callback = new BrowserStatusCallback(IID, ok -> {
            calls.incrementAndGet();
            result.set(ok);
            throw new IllegalStateException("Test callback failure");
        })) {
            assertEquals(0x80004005, call(callback.pointer(), 3, 0x80004005));
            assertEquals(Boolean.FALSE, result.get());
            assertEquals(0, call(callback.pointer(), 3, 0));
            assertEquals(1, calls.get());
        }
    }

    @Test
    void reentrantReleaseKeepsTheObjectPinnedThroughInvoke() {
        int baseline = BrowserStatusCallback.retainedCount();
        var owner = new AtomicReference<BrowserStatusCallback>();

        try (var callback = new BrowserStatusCallback(IID, ok -> {
            owner.get().close();
            assertEquals(baseline + 1, BrowserStatusCallback.retainedCount());
        })) {
            owner.set(callback);
            assertEquals(0, call(callback.pointer(), 3, 0));
            assertEquals(baseline, BrowserStatusCallback.retainedCount());
        }
    }

    private static int call(Pointer self, int slot, Object... arguments) {
        Object[] args = new Object[arguments.length + 1];
        args[0] = self;
        System.arraycopy(arguments, 0, args, 1, arguments.length);

        var function = self.getPointer(0).getPointer((long) slot * Native.POINTER_SIZE);

        return Function.getFunction(function, Function.ALT_CONVENTION).invokeInt(args);
    }
}
