package to.sparkapp.app.ui.topbar.components;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import to.sparkapp.app.config.AiConfiguration.AiConfig;
import to.sparkapp.app.config.TestPreferences;
import to.sparkapp.app.support.JavaFxTestRuntime;
import to.sparkapp.app.ui.webview.FxWebViewPane;

@Tag("native")
@Timeout(20)
class AiDockOrderListenersTest {
    @TempDir
    Path directory;

    @Test
    void everyListenerHearsAboutANewOrder() throws Exception {
        JavaFxTestRuntime.call(() -> {
            var preferences = TestPreferences.at(directory.resolve("settings.json").toFile());
            var browser = new FxWebViewPane("about:blank", preferences);
            var sites = Stream.of("one", "two", "three")
                    .map(id -> new AiConfig(id, id, "https://example.com/" + id, null, null)).toList();
            var dock = new AiDock(sites, browser, preferences);
            var sitesList = new AtomicInteger();
            var tray = new AtomicInteger();

            try {
                dock.addOrderListener(sitesList::incrementAndGet);
                dock.addOrderListener(tray::incrementAndGet);

                dock.reorder("one", "three", true);

                assertEquals(1, sitesList.get());
                assertEquals(1, tray.get());
                assertEquals(List.of("two", "three", "one"), preferences.getAiOrder());
            } finally {
                dock.dispose();
                browser.shutdown(() -> {
                });
            }

            return null;
        });
    }
}
