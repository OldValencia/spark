package to.sparkapp.app.browser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Reads CPU time and memory of one WebView2 browser process and all of its descendants through WMI.
 */
final class WebviewProcessSampler {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String QUERY = """
            Get-CimInstance Win32_Process -Filter "Name='msedgewebview2.exe'" |
              Select-Object ProcessId,ParentProcessId,CommandLine,KernelModeTime,UserModeTime,WorkingSetSize,PrivatePageCount |
              ConvertTo-Json -Compress
            """;

    record Process(long pid, String type, long cpuNanos, long workingSet, long privateBytes) {
    }

    record Snapshot(long takenAt, Map<Long, Process> processes) {
        long workingSet(String type) {
            return processes.values().stream().filter(p -> matches(p, type)).mapToLong(Process::workingSet).sum();
        }

        long privateBytes(String type) {
            return processes.values().stream().filter(p -> matches(p, type)).mapToLong(Process::privateBytes).sum();
        }

        /** CPU used since an earlier snapshot, as a percentage of one core. */
        double cpuPercentSince(Snapshot earlier, String type) {
            var used = 0L;

            for (var process : processes.values()) {
                if (matches(process, type)) {
                    var before = earlier.processes.get(process.pid());
                    used += process.cpuNanos() - (before == null ? 0 : before.cpuNanos());
                }
            }

            return 100.0 * used / (takenAt - earlier.takenAt);
        }

        private static boolean matches(Process process, String type) {
            return type == null || process.type().equals(type);
        }
    }

    private final long browserPid;

    WebviewProcessSampler(long browserPid) {
        this.browserPid = browserPid;
    }

    Snapshot sample() throws IOException, InterruptedException {
        var all = readAll();
        var tree = descendants(all);
        var processes = new HashMap<Long, Process>();

        for (var pid : tree) {
            processes.put(pid, describe(all.get(pid)));
        }

        return new Snapshot(System.nanoTime(), processes);
    }

    private Map<Long, JsonNode> readAll() throws IOException, InterruptedException {
        // An encoded command survives Windows argument quoting unchanged.
        var encoded = Base64.getEncoder().encodeToString(QUERY.getBytes(StandardCharsets.UTF_16LE));
        var command = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        var output = new String(command.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        command.waitFor();

        var root = JSON.readTree(output);
        var byPid = new HashMap<Long, JsonNode>();

        for (var node : root.isArray() ? root : JSON.createArrayNode().add(root)) {
            byPid.put(node.get("ProcessId").asLong(), node);
        }

        return byPid;
    }

    private HashSet<Long> descendants(Map<Long, JsonNode> all) {
        var tree = new HashSet<Long>();
        tree.add(browserPid);
        var grew = true;

        while (grew) {
            grew = false;

            for (var node : all.values()) {
                var pid = node.get("ProcessId").asLong();

                if (!tree.contains(pid) && tree.contains(node.get("ParentProcessId").asLong())) {
                    tree.add(pid);
                    grew = true;
                }
            }
        }

        tree.retainAll(all.keySet());

        return tree;
    }

    private static Process describe(JsonNode node) {
        var cpu = (node.get("KernelModeTime").asLong() + node.get("UserModeTime").asLong()) * 100;

        return new Process(node.get("ProcessId").asLong(), type(node.path("CommandLine").asText("")), cpu,
                node.get("WorkingSetSize").asLong(), node.get("PrivatePageCount").asLong());
    }

    private static String type(String commandLine) {
        var marker = "--type=";
        var start = commandLine.indexOf(marker);

        if (start < 0) {
            return "browser";
        }

        var end = commandLine.indexOf(' ', start);

        return commandLine.substring(start + marker.length(), end < 0 ? commandLine.length() : end);
    }
}
