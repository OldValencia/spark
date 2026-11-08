package to.sparkapp.app.browser;

import java.awt.Rectangle;
import java.awt.Robot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;

import com.sun.jna.WString;

import javax.imageio.ImageIO;

import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import to.sparkapp.app.support.JavaFxTestRuntime;

final class ExtensionPopupPreview {
    static Map<String, Object> capture(String name, String id, String page, String file) throws Exception {
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
            created.open(owner, name, id, page, null);
            return created;
        });
        try {
            var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12);
            while (System.nanoTime() < deadline
                    && !JavaFxTestRuntime.call(() -> popup.getWindow().getOpacity() == 1)) {
                new CountDownLatch(1).await(40, TimeUnit.MILLISECONDS);
            }
            var interactive = false;
            var readyDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12);
            while (System.nanoTime() < readyDeadline && JavaFxTestRuntime.call(() -> popup.getWindow().isShowing())) {
                var view = popup.getBrowser();
                if (view != null && hasVisibleControls(view)) {
                    interactive = true;
                    break;
                }
                new CountDownLatch(1).await(200, TimeUnit.MILLISECONDS);
            }
            new CountDownLatch(1).await(500, TimeUnit.MILLISECONDS);
            var bounds = JavaFxTestRuntime.call(() -> new Rectangle((int) popup.getWindow().getX(),
                    (int) popup.getWindow().getY(), (int) popup.getWindow().getWidth(), (int) popup.getWindow().getHeight()));
            var showing = JavaFxTestRuntime.call(() -> popup.getWindow().isShowing());
            if (showing) {
                var target = Path.of("build", "previews", "extension-" + file + ".png");
                Files.createDirectories(target.getParent());
                ImageIO.write(new Robot().createScreenCapture(bounds), "png", target.toFile());
            }
            return Map.of("width", bounds.width, "height", bounds.height, "showing", showing, "interactive", interactive);
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
        }
    }

    private static boolean hasVisibleControls(Webview view) throws Exception {
        var result = new CompletableFuture<Boolean>();
        view.dispatch(() -> {
            try (var callback = new BrowserAsyncCallback("49511172-cc67-4bca-9923-137112f4c4cc", (status, value) ->
                    result.complete(status >= 0 && value != null && "true".equals(value.getWideString(0))))) {
                var script = """
                        Array.from(document.querySelectorAll('button,input,a[href]')).some(element => {
                            const box = element.getBoundingClientRect();
                            return box.width > 0 && box.height > 0 && getComputedStyle(element).visibility !== 'hidden';
                        })
                        """;
                ComCalls.check(ComCalls.call(view.windows().activeContext().getCore(), 29, new WString(script), callback.pointer()));
            } catch (Exception error) {
                result.completeExceptionally(error);
            }
        });
        return result.get(5, TimeUnit.SECONDS);
    }
}
