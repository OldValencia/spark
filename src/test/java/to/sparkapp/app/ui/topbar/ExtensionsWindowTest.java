package to.sparkapp.app.ui.topbar;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import to.sparkapp.app.browser.WebviewManager;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.extensions.ExtensionManager;
import to.sparkapp.app.support.JavaFxTestRuntime;
import to.sparkapp.app.ui.extensions.ExtensionsPanel;
import to.sparkapp.app.windows.SettingsWindow;

@Tag("native")
@Timeout(100)
class ExtensionsWindowTest {
    @Test
    void rendersTheFourImportedExtensionsInTheOwnedWindow() throws Exception {
        var packages = Path.of("local", "extensions-samples");
        assumeTrue(Files.isRegularFile(packages.resolve("bitwarden.zip")), "Optional sample packages are absent");
        JavaFxTestRuntime.start();
        var browser = new WebviewManager(new AppPreferences());
        browser.init("about:blank", 0, 0, 0, 600, 400);
        var manager = new ExtensionManager();
        var entries = manager.refresh(browser).get(25, TimeUnit.SECONDS);
        for (var entry : entries) {
            manager.remove(browser, entry).get(25, TimeUnit.SECONDS);
        }
        for (var file : List.of("bitwarden.zip", "ublock.zip", "cookies.crx", "darkreader.crx")) {
            entries = manager.importArchive(browser, packages.resolve(file)).get(25, TimeUnit.SECONDS);
        }
        assertEquals(4, entries.size());
        assertTrue(entries.stream().allMatch(entry -> entry.installed()));
        var panel = JavaFxTestRuntime.call(() -> new ExtensionsPanel(() -> browser));
        var owner = JavaFxTestRuntime.call(() -> {
            var stage = new Stage();
            stage.setScene(new Scene(new StackPane(), 860, 800));
            stage.setX(-20000);
            stage.setY(-20000);
            stage.show();
            return stage;
        });
        var window = JavaFxTestRuntime.call(() -> {
            var created = new SettingsWindow(owner, panel, "Extensions");
            created.open();
            panel.refresh();
            assertTrue(panel.lookup(".extension-progress").isVisible(), "Refresh must immediately show a loading indicator");
            return created;
        });
        try {
            var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(25);
            while (System.nanoTime() < deadline && JavaFxTestRuntime.call(() ->
                    panel.lookupAll(".extension-card").size() < 4 || panel.lookup(".extension-progress").isVisible())) {
                new CountDownLatch(1).await(30, TimeUnit.MILLISECONDS);
            }
            JavaFxTestRuntime.call(() -> {
                assertEquals(4, panel.lookupAll(".extension-card").size());
                assertFalse(panel.lookup(".extension-progress").isVisible());
                var screenshot = Path.of("build", "previews", "extensions-manager.png");
                Files.createDirectories(screenshot.getParent());
                ImageIO.write(SwingFXUtils.fromFXImage(panel.getScene().snapshot(null), null), "png", screenshot.toFile());
                return null;
            });
        } finally {
            JavaFxTestRuntime.call(() -> {
                window.close();
                owner.close();
                return null;
            });
            for (var entry : entries) {
                manager.remove(browser, entry).get(25, TimeUnit.SECONDS);
            }
            var stopped = new CompletableFuture<Void>();
            browser.shutdown(() -> stopped.complete(null));
            stopped.get(10, TimeUnit.SECONDS);
        }
    }
}
