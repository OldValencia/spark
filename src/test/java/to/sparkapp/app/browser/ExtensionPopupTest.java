package to.sparkapp.app.browser;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.sun.jna.WString;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.RECT;

import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import to.sparkapp.app.support.JavaFxTestRuntime;
import to.sparkapp.app.utils.NativeWindowUtils;

@Tag("native")
@Timeout(45)
class ExtensionPopupTest {
    @TempDir
    Path directory;

    @Test
    void ownsTheNativePopupAndClosesItsBrowserWhenTheManagerCloses() throws Exception {
        Files.writeString(directory.resolve("manifest.json"), """
                {"manifest_version":3,"name":"Popup lifecycle fixture","version":"1.0",
                 "action":{"default_popup":"popup.html"},"background":{"service_worker":"worker.js"}}
                """);
        Files.writeString(directory.resolve("popup.html"), """
                <html><body style="margin:0;width:260px;height:180px">
                Native popup fixture
                <div style="height:20px;overflow:hidden"><div style="height:2000px">Clipped internal panel</div></div>
                </body></html>
                """);
        Files.writeString(directory.resolve("worker.js"), "chrome.runtime.onMessage.addListener((message,sender,respond)=>respond(true));");
        try (var browser = new WindowsBrowserTest.Harness()) {
            var extension = BrowserExtensionsTest.call(browser, nativeExtensions -> nativeExtensions.install(directory));
            var windowId = ExtensionContextTest.command(browser, "Browser.getWindowForTarget", Map.of()).path("windowId").asInt();
            var owner = JavaFxTestRuntime.call(() -> {
                var stage = new Stage();
                stage.setScene(new Scene(new StackPane(), 860, 700));
                stage.setX(-20000);
                stage.setY(-20000);
                stage.show();
                return stage;
            });
            var popup = JavaFxTestRuntime.call(() -> {
                var created = new ExtensionPopup();
                created.open(owner, "Extension popup fixture", extension.id(), "popup.html", windowId);
                return created;
            });
            try {
                var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
                String source = "";
                while (System.nanoTime() < deadline && !source.endsWith("/popup.html")) {
                    var view = popup.getBrowser();
                    if (view != null) {
                        var result = new CompletableFuture<String>();
                        view.dispatch(() -> {
                            try {
                                result.complete(view.windows().source());
                            } catch (Exception error) {
                                result.completeExceptionally(error);
                            }
                        });
                        source = result.get(5, TimeUnit.SECONDS);
                    }
                    new CountDownLatch(1).await(20, TimeUnit.MILLISECONDS);
                }
                assertEquals("chrome-extension://" + extension.id() + "/popup.html", source);
                var parent = JavaFxTestRuntime.call(() -> NativeWindowUtils.getJavaFXWindowHandle(popup.getWindow().getTitle()));
                var isChild = new CompletableFuture<Boolean>();
                var view = popup.getBrowser();
                view.dispatch(() -> isChild.complete(NativeWindowUtils.isChildOf(view.getNativeWindowPointer(), parent)));
                assertTrue(isChild.get(5, TimeUnit.SECONDS));
                awaitSize(popup, 272, 192);
                var nativeBounds = new CompletableFuture<int[]>();
                view.dispatch(() -> {
                    var handle = new HWND(new Pointer(view.getNativeWindowPointer()));
                    var outer = new RECT();
                    var client = new RECT();
                    User32.INSTANCE.GetWindowRect(handle, outer);
                    User32.INSTANCE.GetClientRect(handle, client);
                    nativeBounds.complete(new int[]{outer.right - outer.left - client.right,
                            outer.bottom - outer.top - client.bottom});
                });
                assertArrayEquals(new int[]{0, 0}, nativeBounds.get(5, TimeUnit.SECONDS),
                        "The queued native initialization must not restore a non-client border");
                assertEquals(StageStyle.UNDECORATED, JavaFxTestRuntime.call(() -> popup.getWindow().getStyle()));
                view.dispatch(() -> view.eval("document.body.style.width='360px';document.body.style.height='240px'"));
                awaitSize(popup, 372, 252);
                var viewport = new CompletableFuture<String>();
                view.dispatch(() -> {
                    try (var callback = new BrowserAsyncCallback("49511172-cc67-4bca-9923-137112f4c4cc",
                            (status, result) -> viewport.complete(result.getWideString(0)))) {
                        ComCalls.check(ComCalls.call(view.windows().activeContext().getCore(), 29,
                                new WString("[innerWidth,innerHeight]"), callback.pointer()));
                    }
                });
                assertEquals("[360,240]", viewport.get(5, TimeUnit.SECONDS));
                view.dispatch(() -> view.eval("document.body.style.width='260px';document.body.style.height='180px'"));
                awaitSize(popup, 272, 192);
                JavaFxTestRuntime.call(() -> {
                    owner.setX(100);
                    owner.setY(100);
                    owner.toFront();
                    owner.requestFocus();
                    NativeWindowUtils.restoreForeground(NativeWindowUtils.getJavaFXWindowHandle(owner.getTitle()));
                    return null;
                });
                var dismissalDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (JavaFxTestRuntime.call(() -> popup.getWindow().isShowing()) && System.nanoTime() < dismissalDeadline) {
                    new CountDownLatch(1).await(40, TimeUnit.MILLISECONDS);
                }
                assertFalse(JavaFxTestRuntime.call(() -> popup.getWindow().isShowing()),
                        "Switching to the owner must dismiss the popup");
            } finally {
                JavaFxTestRuntime.call(() -> {
                    owner.close();
                    popup.getWindow().close();
                    return null;
                });
                var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (popup.getBrowser() != null && System.nanoTime() < deadline) {
                    new CountDownLatch(1).await(20, TimeUnit.MILLISECONDS);
                }
                assertNull(popup.getBrowser(), "Closing the owner must finish native teardown");
                BrowserExtensionsTest.call(browser, nativeExtensions -> nativeExtensions.remove(extension.id()));
            }
        }
    }

    private static void awaitSize(ExtensionPopup popup, int width, int height) throws Exception {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            var matches = JavaFxTestRuntime.call(() -> popup.getWindow().getOpacity() == 1
                    && Math.abs(popup.getWindow().getWidth() - width) < 2
                    && Math.abs(popup.getWindow().getHeight() - height) < 2);
            if (matches) {
                new CountDownLatch(1).await(300, TimeUnit.MILLISECONDS);
                return;
            }
            new CountDownLatch(1).await(40, TimeUnit.MILLISECONDS);
        }
        fail("Popup did not resize to " + width + "x" + height + ": "
                + JavaFxTestRuntime.call(() -> popup.getWindow().getWidth() + "x" + popup.getWindow().getHeight()));
    }
}
