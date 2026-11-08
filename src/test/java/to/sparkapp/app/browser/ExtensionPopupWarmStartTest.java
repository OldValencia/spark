package to.sparkapp.app.browser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.ptr.IntByReference;

import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import to.sparkapp.app.support.JavaFxTestRuntime;

@Tag("native")
@Timeout(60)
class ExtensionPopupWarmStartTest {
    @TempDir
    Path directory;

    @Test
    void preparedBrowsersOpenRepeatedlyAndAreReleasedWhenPreparationStops() throws Exception {
        Files.writeString(directory.resolve("manifest.json"), """
                {"manifest_version":3,"name":"Warm popup fixture","version":"1.0","action":{"default_popup":"popup.html"}}
                """);
        Files.writeString(directory.resolve("popup.html"), "<html><body style='margin:0;width:240px;height:120px'>Warm</body></html>");
        try (var site = new WindowsBrowserTest.Harness()) {
            var extension = BrowserExtensionsTest.call(site, extensions -> extensions.install(directory));
            var owner = JavaFxTestRuntime.call(ExtensionPopupWarmStartTest::owner);
            try {
                var visibleBefore = visibleTopLevelWindows();
                ExtensionPopup.prepareForOpening();
                new CountDownLatch(1).await(1500, TimeUnit.MILLISECONDS);
                assertEquals(visibleBefore, visibleTopLevelWindows(), "A prepared popup browser must not show a window");
                for (var round = 1; round <= 2; round++) {
                    var startedAt = System.nanoTime();
                    var popup = JavaFxTestRuntime.call(() -> {
                        var created = new ExtensionPopup();
                        created.open(owner, "Warm popup fixture", extension.id(), "popup.html", null);
                        return created;
                    });
                    awaitVisible(popup);
                    System.out.println("Warm popup visible after " + (System.nanoTime() - startedAt) / 1_000_000 + " ms");
                    JavaFxTestRuntime.call(() -> {
                        popup.getWindow().close();
                        return null;
                    });
                    awaitReleased(popup);
                    new CountDownLatch(1).await(500, TimeUnit.MILLISECONDS);
                }
            } finally {
                ExtensionPopup.stopPreparing();
                JavaFxTestRuntime.call(() -> {
                    owner.close();
                    return null;
                });
                BrowserExtensionsTest.call(site, extensions -> extensions.remove(extension.id()));
            }
        }
    }

    @Test
    void clickingTheButtonOfAnOpenPopupClosesIt() throws Exception {
        Files.writeString(directory.resolve("manifest.json"), """
                {"manifest_version":3,"name":"Toggle fixture","version":"1.0","action":{"default_popup":"popup.html"}}
                """);
        Files.writeString(directory.resolve("popup.html"), "<html><body style='margin:0;width:200px;height:100px'>Toggle</body></html>");
        try (var site = new WindowsBrowserTest.Harness()) {
            var extension = BrowserExtensionsTest.call(site, extensions -> extensions.install(directory));
            var owner = JavaFxTestRuntime.call(ExtensionPopupWarmStartTest::owner);
            try {
                var popup = JavaFxTestRuntime.call(() -> {
                    var created = new ExtensionPopup();
                    created.open(owner, "Toggle fixture", extension.id(), "popup.html", null);
                    return created;
                });
                awaitVisible(popup);

                var pressedAt = System.nanoTime();
                assertTrue(JavaFxTestRuntime.call(() -> ExtensionPopup.closeIfShowing(extension.id(), "popup.html", pressedAt)));
                assertFalse(JavaFxTestRuntime.call(() -> popup.getWindow().isShowing()), "The toggle must close the open popup");
                assertTrue(JavaFxTestRuntime.call(() -> ExtensionPopup.closeIfShowing(extension.id(), "popup.html", pressedAt)),
                        "A click whose press already dismissed the popup must not reopen it");

                var laterPress = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
                assertFalse(JavaFxTestRuntime.call(() -> ExtensionPopup.closeIfShowing(extension.id(), "popup.html", laterPress)),
                        "A later click opens the popup again");
                assertFalse(JavaFxTestRuntime.call(() -> ExtensionPopup.closeIfShowing(extension.id(), "options.html", pressedAt)),
                        "Another page of the extension is not the same popup");
                awaitReleased(popup);
            } finally {
                JavaFxTestRuntime.call(() -> {
                    owner.close();
                    return null;
                });
                BrowserExtensionsTest.call(site, extensions -> extensions.remove(extension.id()));
            }
        }
    }

    /** Visible top-level windows of this process; a prepared browser must not add one. */
    private static int visibleTopLevelWindows() {
        var process = Kernel32.INSTANCE.GetCurrentProcessId();
        var count = new AtomicInteger();
        User32.INSTANCE.EnumWindows((window, data) -> {
            var owner = new IntByReference();
            User32.INSTANCE.GetWindowThreadProcessId(window, owner);
            if (owner.getValue() == process && User32.INSTANCE.IsWindowVisible(window)) {
                count.incrementAndGet();
            }
            return true;
        }, null);

        return count.get();
    }

    private static void awaitVisible(ExtensionPopup popup) throws Exception {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (JavaFxTestRuntime.call(() -> popup.getWindow().getOpacity() == 1)) {
                return;
            }
            new CountDownLatch(1).await(10, TimeUnit.MILLISECONDS);
        }
        assertTrue(false, "The prepared popup did not become visible");
    }

    private static void awaitReleased(ExtensionPopup popup) throws Exception {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (popup.getBrowser() != null && System.nanoTime() < deadline) {
            new CountDownLatch(1).await(20, TimeUnit.MILLISECONDS);
        }
        assertNull(popup.getBrowser(), "Closing must release the popup browser");
    }

    private static Stage owner() {
        var stage = new Stage();
        stage.setScene(new Scene(new StackPane(), 600, 500));
        stage.setX(-20000);
        stage.setY(-20000);
        stage.show();
        return stage;
    }
}
