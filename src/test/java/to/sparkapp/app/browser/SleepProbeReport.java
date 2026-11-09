package to.sparkapp.app.browser;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Collects the phases of {@link SiteSleepProbe} and prints them as one table.
 */
final class SleepProbeReport {
    private static final double MB = 1024.0 * 1024.0;
    private static final String RENDERER = "renderer";

    private final List<String> lines = new ArrayList<>();

    void measure(String site, String phase, WebviewProcessSampler sampler, Duration duration)
            throws IOException, InterruptedException {
        var before = sampler.sample();
        Thread.sleep(duration.toMillis());
        var after = sampler.sample();

        var line = String.format(Locale.ROOT, "%-46s %-20s %7.2f %7.2f %8.0f %8.0f %8.0f %8.0f", site, phase,
                after.cpuPercentSince(before, null), after.cpuPercentSince(before, RENDERER),
                after.workingSet(null) / MB, after.workingSet(RENDERER) / MB,
                after.privateBytes(null) / MB, after.privateBytes(RENDERER) / MB);
        add(line);
    }

    void note(String site, String text) {
        add(String.format(Locale.ROOT, "%-46s   %s", site, text));
    }

    void print() {
        System.out.println();
        System.out.println(header());
        lines.forEach(System.out::println);
    }

    private void add(String line) {
        if (lines.isEmpty()) {
            System.out.println(header());
        }

        lines.add(line);
        System.out.println(line);
    }

    private static String header() {
        return String.format(Locale.ROOT, "%-46s %-20s %7s %7s %8s %8s %8s %8s", "site", "phase", "cpu%", "rend%",
                "wsMB", "rendWS", "privMB", "rendPriv");
    }
}
