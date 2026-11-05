package to.sparkapp.app.browser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.jna.Pointer;
import org.junit.jupiter.api.Test;
import to.sparkapp.app.browser.WebviewNative.BindCallback;
import to.sparkapp.app.browser.WebviewNative.DispatchCallback;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class WebviewTest {
    @Test
    void nativeErrorsAreReportedWithoutReplacingRegisteredBindings() throws Exception {
        var fake = new FakeNative();
        var view = new Webview(false, fake.api());
        try {
            view.bind("probe", args -> "null");
            Object original = retained(view, "activeBinds");
            Object originalCallback = ((Map<?, ?>) original).get("probe");
            fake.results.put("webview_bind", 1);
            assertTrue(assertThrows(IllegalStateException.class,
                    () -> view.bind("probe", args -> "true")).getMessage().contains("code 1"));
            assertSame(originalCallback, ((Map<?, ?>) retained(view, "activeBinds")).get("probe"));
            fake.results.put("webview_bind", -2);
            assertThrows(IllegalStateException.class, () -> view.bind("other", args -> "true"));
            assertEquals(1, ((Map<?, ?>) retained(view, "activeBinds")).size());
            fake.results.put("webview_navigate", -3);
            assertTrue(assertThrows(IllegalStateException.class,
                    () -> view.loadURL("about:blank")).getMessage().contains("code -3"));
            fake.results.put("webview_init", -1);
            assertThrows(IllegalStateException.class, () -> view.setInitScript("void 0"));
            fake.results.put("webview_terminate", -2);
            assertThrows(IllegalStateException.class, view::close);
        } finally {
            view.destroy();
        }
    }

    @Test
    void failedDispatchReleasesItsCallbackAndCallbackExceptionsStayInJava() throws Exception {
        var fake = new FakeNative();
        var view = new Webview(false, fake.api());
        try {
            fake.results.put("webview_dispatch", -3);
            assertThrows(IllegalStateException.class, () -> view.dispatch(() -> {}));
            assertEquals(0, ((Set<?>) retained(view, "activeDispatches")).size());
            fake.results.put("webview_dispatch", 0);
            view.dispatch(() -> { throw new IllegalStateException("Test task failure"); });
            assertEquals(1, ((Set<?>) retained(view, "activeDispatches")).size());
            assertDoesNotThrow(() -> fake.dispatched.callback(fake.pointer, null));
            assertTrue(((Set<?>) retained(view, "activeDispatches")).isEmpty());
        } finally {
            view.destroy();
        }
    }

    @Test
    void destructionDiscardsQueuedTasksAndClearsCallbacksEvenOnNativeError() throws Exception {
        var fake = new FakeNative();
        var view = new Webview(false, fake.api());
        var executed = new AtomicBoolean();
        view.bind("probe", args -> "null");
        view.dispatch(() -> executed.set(true));
        var stale = fake.dispatched;
        fake.results.put("webview_destroy", -1);
        assertThrows(IllegalStateException.class, view::destroy);
        assertTrue(((Map<?, ?>) retained(view, "activeBinds")).isEmpty());
        assertTrue(((Set<?>) retained(view, "activeDispatches")).isEmpty());
        stale.callback(fake.pointer, null);
        view.dispatch(() -> executed.set(true));
        view.destroy();
        assertFalse(executed.get());
        assertEquals(1, fake.count("webview_destroy"));
    }

    @Test
    void foreignThreadsMustUseDispatch() throws Exception {
        var fake = new FakeNative();
        var view = new Webview(false, fake.api());
        var executed = new AtomicBoolean();
        try {
            CompletableFuture.runAsync(() -> {
                assertThrows(IllegalStateException.class, () -> view.loadURL("about:blank"));
                assertThrows(IllegalStateException.class, () -> view.setInitScript("void 0"));
                assertThrows(IllegalStateException.class, view::getNativeWindowPointer);
                assertThrows(IllegalStateException.class, view::windows);
                assertThrows(IllegalStateException.class, view::close);
                assertThrows(IllegalStateException.class, view::destroy);
                view.dispatch(() -> executed.set(true));
            }).get(5, TimeUnit.SECONDS);
            assertFalse(executed.get());
            assertEquals(0, fake.count("webview_navigate"));
            assertEquals(0, fake.count("webview_terminate"));
            fake.dispatched.callback(fake.pointer, null);
            assertTrue(executed.get());
        } finally {
            view.destroy();
        }
    }

    @Test
    void bindingPreservesStringRequestIdsAndReturnsValidJsonForErrors() throws Exception {
        var fake = new FakeNative();
        var view = new Webview(false, fake.api());
        String message = "Test path C:\\draft\\note with \"quotes\"\nnext line\tand tab";
        try {
            view.bind("probe", args -> { throw new IllegalArgumentException(message); });
            fake.bound.callback("request-not-a-number", "[]", null);
            assertEquals("request-not-a-number", fake.returned[1]);
            assertEquals(1, fake.returned[2]);
            var rejection = new ObjectMapper().readTree((String) fake.returned[3]);
            assertTrue(rejection.isTextual());
            assertTrue(rejection.asText().contains(message));
            view.bind("success", args -> null);
            fake.bound.callback("request-2", "[]", null);
            assertEquals("request-2", fake.returned[1]);
            assertEquals(0, fake.returned[2]);
            assertEquals("null", fake.returned[3]);
        } finally {
            view.destroy();
        }
    }

    @Test
    void failedEventLoopStillDestroysNativeBrowser() {
        var fake = new FakeNative();
        var view = new Webview(false, fake.api());
        fake.results.put("webview_run", -1);
        assertThrows(IllegalStateException.class, view::run);
        assertEquals(1, fake.count("webview_destroy"));
    }

    private static Object retained(Webview view, String name) throws Exception {
        var field = Webview.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(view);
    }

    /** No native library, browser process or user profile is opened by this test double. */
    private static class FakeNative implements InvocationHandler {
        final Pointer pointer = new Pointer(1);
        final Map<String, Integer> results = new HashMap<>();
        final List<String> calls = new ArrayList<>();
        BindCallback bound;
        DispatchCallback dispatched;
        Object[] returned;

        WebviewNative api() {
            return (WebviewNative) Proxy.newProxyInstance(WebviewNative.class.getClassLoader(),
                    new Class<?>[]{WebviewNative.class}, this);
        }

        int count(String name) { return (int) calls.stream().filter(name::equals).count(); }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            calls.add(name);
            if (name.equals("webview_create")) return pointer;
            if (name.equals("webview_bind")) bound = (BindCallback) args[2];
            if (name.equals("webview_dispatch")) dispatched = (DispatchCallback) args[1];
            if (name.equals("webview_return")) returned = args;
            if (method.getReturnType() == int.class) return results.getOrDefault(name, 0);
            return null;
        }
    }
}
