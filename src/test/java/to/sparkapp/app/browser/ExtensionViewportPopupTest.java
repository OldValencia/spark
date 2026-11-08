package to.sparkapp.app.browser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import to.sparkapp.app.support.JavaFxTestRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("native")
@Timeout(45)
class ExtensionViewportPopupTest {
    @TempDir
    Path directory;

    @Test
    void viewportRelativeLayoutsDoNotCollapseToTheLoadingHeight() throws Exception {
        Files.writeString(directory.resolve("manifest.json"), """
                {"manifest_version":3,"name":"Viewport popup fixture","version":"1.0",
                 "action":{"default_popup":"popup.html"}}
                """);
        Files.writeString(directory.resolve("popup.html"), """
                <!doctype html><html><head><style>
                html,body { margin:0; width:100%; height:100%; overflow:hidden; }
                header { height:64px; }
                main { position:absolute; inset:64px 0 0; overflow:auto; }
                .scroll-content { height:1200px; }
                </style></head><body><header>Viewport popup fixture</header>
                <main><div class="scroll-content">Scrollable content</div><button>Continue</button></main>
                </body></html>
                """);
        try (var browser = new WindowsBrowserTest.Harness()) {
            var extension = BrowserExtensionsTest.call(browser, extensions -> extensions.install(directory));
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
                created.open(owner, "Viewport popup fixture", extension.id(), "popup.html", null);
                return created;
            });
            try {
                var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (System.nanoTime() < deadline
                        && JavaFxTestRuntime.call(() -> popup.getWindow().getOpacity() != 1)) {
                    new CountDownLatch(1).await(40, TimeUnit.MILLISECONDS);
                }
                new CountDownLatch(1).await(1500, TimeUnit.MILLISECONDS);
                JavaFxTestRuntime.call(() -> {
                    assertTrue(popup.getWindow().isShowing());
                    assertEquals(432, popup.getWindow().getWidth(), 1);
                    assertEquals(612, popup.getWindow().getHeight(), 1,
                            "A 100% height popup must retain a usable viewport without expanding its internal scroll panel");
                    return null;
                });
            } finally {
                JavaFxTestRuntime.call(() -> {
                    popup.getWindow().close();
                    owner.close();
                    return null;
                });
                var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (popup.getBrowser() != null && System.nanoTime() < deadline) {
                    new CountDownLatch(1).await(20, TimeUnit.MILLISECONDS);
                }
                assertNull(popup.getBrowser());
                BrowserExtensionsTest.call(browser, extensions -> extensions.remove(extension.id()));
            }
        }
    }
}
