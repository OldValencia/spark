package to.sparkapp.app.extensions;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import to.sparkapp.app.browser.WebviewManager;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.support.JavaFxTestRuntime;

@Tag("native")
@Timeout(80)
class ExtensionManagementTest {
    @Test
    void refreshPreservesDisabledStateAndRemovalDoesNotReinstallOnRestart() throws Exception {
        JavaFxTestRuntime.start();
        var store = new ExtensionStore();
        var directory = Files.createTempDirectory(store.root(), "management-test-");
        Files.writeString(directory.resolve("manifest.json"), """
                {"manifest_version":3,"name":"Extension management fixture","version":"1.0",
                 "action":{"default_popup":"popup.html"},"background":{"service_worker":"worker.js"}}
                """);
        Files.writeString(directory.resolve("popup.html"), "<html><body>Popup</body></html>");
        Files.writeString(directory.resolve("worker.js"), "chrome.runtime.onInstalled.addListener(()=>{});");
        var browser = new WebviewManager(new AppPreferences());
        var manager = new ExtensionManager();
        browser.init("about:blank", 0, 0, 0, 600, 400);
        try {
            var original = browser.installExtension(directory).get(25, TimeUnit.SECONDS);
            store.put(directory, new ExtensionStore.Saved(original.id(), ExtensionFingerprint.of(directory), true));
            var entered = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            Consumer<ExtensionProgress> pause = progress -> {
                if (progress.message().equals("Reading installed extensions…")) {
                    entered.countDown();
                    try {
                        release.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                    }
                }
            };
            manager.addProgressListener(pause);
            var pending = manager.refresh(browser);
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                assertSame(pending, manager.refresh(browser), "Opening the manager during startup must join the existing refresh");
            } finally {
                release.countDown();
                manager.removeProgressListener(pause);
            }
            var entries = pending.get(25, TimeUnit.SECONDS);
            var entry = entries.stream().filter(item -> item.manifest().directory().equals(directory)).findFirst().orElseThrow();
            assertTrue(entry.installed(), entry.error());
            assertTrue(entry.enabled(), "Preparing an existing enabled extension must restore its enabled state");
            assertEquals(original.id(), entry.id());
            assertTrue(Files.isRegularFile(directory.resolve(".spark-bootstrap.json")));
            var id = entry.id();
            entries = manager.setEnabled(browser, entry, false).get(25, TimeUnit.SECONDS);
            entry = entries.stream().filter(item -> id.equals(item.id())).findFirst().orElseThrow();
            assertFalse(entry.enabled());
            var restarted = new ExtensionManager();
            entries = restarted.refresh(browser).get(25, TimeUnit.SECONDS);
            entry = entries.stream().filter(item -> id.equals(item.id())).findFirst().orElseThrow();
            assertFalse(entry.enabled());
            entries = restarted.remove(browser, entry).get(25, TimeUnit.SECONDS);
            assertTrue(entries.stream().noneMatch(item -> id.equals(item.id())));
            assertFalse(Files.exists(directory));
            assertTrue(restarted.refresh(browser).get(25, TimeUnit.SECONDS).stream().noneMatch(item -> id.equals(item.id())));
        } finally {
            var stopped = new CompletableFuture<Void>();
            browser.shutdown(() -> stopped.complete(null));
            stopped.get(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void deletingAFolderUnloadsItsExtensionButKeepsBuiltInComponents() throws Exception {
        JavaFxTestRuntime.start();
        var store = new ExtensionStore();
        var directory = Files.createTempDirectory(store.root(), "orphan-test-");
        Files.writeString(directory.resolve("manifest.json"), """
                {"manifest_version":3,"name":"Orphan fixture","version":"1.0"}
                """);
        var browser = new WebviewManager(new AppPreferences());
        var manager = new ExtensionManager();
        browser.init("about:blank", 0, 0, 0, 600, 400);
        try {
            var builtIn = browser.listExtensions().get(25, TimeUnit.SECONDS);
            var entries = manager.refresh(browser).get(25, TimeUnit.SECONDS);
            var id = entries.stream().filter(item -> item.manifest().directory().equals(directory))
                    .findFirst().orElseThrow().id();
            assertNotNull(id);

            try (var files = Files.walk(directory)) {
                for (var path : files.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
            manager.refresh(browser).get(25, TimeUnit.SECONDS);

            var remaining = browser.listExtensions().get(25, TimeUnit.SECONDS);
            assertTrue(remaining.stream().noneMatch(item -> item.id().equals(id)), "A deleted folder must unload its extension");
            for (var component : builtIn) {
                assertTrue(remaining.stream().anyMatch(item -> item.id().equals(component.id())),
                        "Built-in components must stay installed: " + component.name());
            }
        } finally {
            var stopped = new CompletableFuture<Void>();
            browser.shutdown(() -> stopped.complete(null));
            stopped.get(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void importingANewerPackageUpdatesTheInstalledExtensionInPlace() throws Exception {
        JavaFxTestRuntime.start();
        var packages = Files.createTempDirectory("update-packages-");
        var browser = new WebviewManager(new AppPreferences());
        var manager = new ExtensionManager();
        browser.init("about:blank", 0, 0, 0, 600, 400);
        try {
            var entries = manager.importArchive(browser, zip(packages, "1.0")).get(25, TimeUnit.SECONDS);
            var original = findUpdateFixture(entries);
            entries = manager.setEnabled(browser, original, false).get(25, TimeUnit.SECONDS);

            entries = manager.importArchive(browser, zip(packages, "2.0")).get(25, TimeUnit.SECONDS);
            var updated = findUpdateFixture(entries);
            assertEquals(1, entries.stream().filter(item -> item.manifest().name().equals("Update fixture")).count(),
                    "A newer package must not create a second copy");
            assertEquals("2.0", updated.manifest().version());
            assertEquals(original.manifest().directory(), updated.manifest().directory());
            assertEquals(original.id(), updated.id(), "The same folder path keeps the extension ID and its data");
            assertFalse(updated.enabled(), "The saved enabled state must survive the update");
            manager.remove(browser, updated).get(25, TimeUnit.SECONDS);
        } finally {
            var stopped = new CompletableFuture<Void>();
            browser.shutdown(() -> stopped.complete(null));
            stopped.get(10, TimeUnit.SECONDS);
        }
    }

    private static ExtensionEntry findUpdateFixture(List<ExtensionEntry> entries) {
        var entry = entries.stream().filter(item -> item.manifest().name().equals("Update fixture")).findFirst().orElseThrow();
        assertTrue(entry.installed(), entry.error());
        return entry;
    }

    private static Path zip(Path directory, String version) throws Exception {
        var archive = directory.resolve("update-fixture-" + version + ".zip");
        try (var zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write(("{\"manifest_version\":3,\"name\":\"Update fixture\",\"version\":\"" + version
                    + "\",\"background\":{\"service_worker\":\"worker.js\"}}").getBytes(StandardCharsets.UTF_8));
            zip.putNextEntry(new ZipEntry("worker.js"));
            zip.write("chrome.runtime.onInstalled.addListener(()=>{});".getBytes(StandardCharsets.UTF_8));
        }
        return archive;
    }
}
