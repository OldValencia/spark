package to.sparkapp.app.browser;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import to.sparkapp.app.utils.NativeWindowUtils;

/**
 * Measures what WebView2's TrySuspend saves on real sites compared with a page that is only hidden.
 * A manual measurement that takes about 12 minutes per site. Run with SPARK_SLEEP_PROBE=1; set
 * SPARK_SLEEP_SITES to a comma-separated list to measure other sites.
 */
@Tag("native")
@EnabledIfEnvironmentVariable(named = "SPARK_SLEEP_PROBE", matches = "1")
class SiteSleepProbe {
    private static final String CORE_3 = "a0d6df20-3b92-416d-aa0c-437a9c727857";
    private static final String SUSPEND_COMPLETED = "00f206a7-9d17-4605-91f6-4e8e4de192e3";
    private static final int TRY_SUSPEND = 68;
    private static final int IS_SUSPENDED = 70;
    private static final int BROWSER_PROCESS_ID = 37;
    private static final String DEFAULT_SITES =
            "https://www.youtube.com/,https://en.wikipedia.org/wiki/World_War_II,https://chatgpt.com/";

    private final SleepProbeReport report = new SleepProbeReport();

    @Test
    @Timeout(value = 90, unit = TimeUnit.MINUTES)
    void measure() throws Exception {
        var sites = System.getenv().getOrDefault("SPARK_SLEEP_SITES", DEFAULT_SITES);

        try (var idle = new WindowsBrowserTest.Harness()) {
            var sampler = new WebviewProcessSampler(onBrowser(idle, SiteSleepProbe::browserProcessId));
            report.measure("(no site)", "baseline", sampler, scaled(Duration.ofSeconds(30)));

            for (var site : List.of(sites.split(","))) {
                measureSite(site.trim(), sampler);
            }
        }

        report.print();
    }

    private void measureSite(String site, WebviewProcessSampler sampler) throws Exception {
        try (var browser = new WindowsBrowserTest.Harness()) {
            browser.load(site);
            present(browser, true, false);
            Thread.sleep(scaled(Duration.ofSeconds(20)).toMillis());
            report.measure(site, "shown", sampler, scaled(Duration.ofSeconds(30)));

            present(browser, false, false);
            report.measure(site, "hidden 0-1 min", sampler, scaled(Duration.ofMinutes(1)));
            report.measure(site, "hidden 1-5 min", sampler, scaled(Duration.ofMinutes(4)));
            report.measure(site, "hidden 5-7 min", sampler, scaled(Duration.ofMinutes(2)));

            var suspended = onBrowser(browser, SiteSleepProbe::trySuspend).get(30, TimeUnit.SECONDS);
            var confirmed = onBrowser(browser, SiteSleepProbe::isSuspended);
            report.note(site, "TrySuspend succeeded: " + suspended + ", IsSuspended: " + confirmed);
            Thread.sleep(scaled(Duration.ofSeconds(30)).toMillis());
            report.measure(site, "suspended", sampler, scaled(Duration.ofMinutes(2)));

            var start = System.nanoTime();
            present(browser, true, false);
            browser.eval("window.probe(document.title)");
            var title = browser.message();
            report.note(site, "Resumed in " + (System.nanoTime() - start) / 1_000_000 + " ms, title: " + title);

            present(browser, false, true);
            Thread.sleep(scaled(Duration.ofSeconds(30)).toMillis());
            report.measure(site, "hidden + Low memory", sampler, scaled(Duration.ofMinutes(1)));
        }
    }

    /** SPARK_SLEEP_SCALE shortens every phase for a quick check of the probe itself. */
    private static Duration scaled(Duration duration) {
        var scale = Double.parseDouble(System.getenv().getOrDefault("SPARK_SLEEP_SCALE", "1"));

        return Duration.ofMillis(Math.round(duration.toMillis() * scale));
    }

    private static void present(WindowsBrowserTest.Harness browser, boolean visible, boolean lowMemory)
            throws Exception {
        onBrowser(browser, view -> {
            NativeWindowUtils.setVisible(view.getNativeWindowPointer(), visible);
            view.windows().setPresentation(visible, lowMemory);

            return null;
        });
    }

    private static long browserProcessId(Webview view) {
        var pid = new IntByReference();
        ComCalls.check(ComCalls.call(view.windows().activeContext().getCore(), BROWSER_PROCESS_ID, pid));

        return pid.getValue();
    }

    private static CompletableFuture<Boolean> trySuspend(Webview view) {
        var result = new CompletableFuture<Boolean>();
        var core = ComCalls.query(view.windows().activeContext().getCore(), CORE_3);

        try (var callback = new BrowserAsyncCallback(SUSPEND_COMPLETED, (status, success) ->
                result.complete(status >= 0 && (Pointer.nativeValue(success) & 0xFFFFFFFFL) != 0))) {
            ComCalls.check(ComCalls.call(core, TRY_SUSPEND, callback.pointer()));
        } finally {
            ComCalls.release(core);
        }

        return result;
    }

    private static boolean isSuspended(Webview view) {
        var core = ComCalls.query(view.windows().activeContext().getCore(), CORE_3);

        try {
            var suspended = new IntByReference();
            ComCalls.check(ComCalls.call(core, IS_SUSPENDED, suspended));

            return suspended.getValue() != 0;
        } finally {
            ComCalls.release(core);
        }
    }

    private static <T> T onBrowser(WindowsBrowserTest.Harness browser, Function<Webview, T> action)
            throws Exception {
        var done = new CompletableFuture<T>();
        browser.run(view -> {
            try {
                done.complete(action.apply(view));
            } catch (Throwable failure) {
                done.completeExceptionally(failure);
            }
        });

        return done.get(15, TimeUnit.SECONDS);
    }
}
