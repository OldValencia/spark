package to.sparkapp.app.browser;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Tag("native")
class NativeLibraryLoadingTest {
    @TempDir Path directory;

    @Test @Timeout(40)
    void independentJvmsCanLoadTheSameCachedDllSimultaneously() throws Exception {
        var first = start("first");
        Process second = null;
        try {
            second = start("second");
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (!(Files.exists(directory.resolve("first.ready")) && Files.exists(directory.resolve("second.ready")))
                    && first.isAlive() && second.isAlive() && System.nanoTime() < deadline) {
                Thread.sleep(25);
            }
            assertTrue(Files.exists(directory.resolve("first.ready")), Files.readString(directory.resolve("first.log")));
            assertTrue(Files.exists(directory.resolve("second.ready")), Files.readString(directory.resolve("second.log")));
            Files.writeString(directory.resolve("release"), "done");
            assertTrue(first.waitFor(10, TimeUnit.SECONDS));
            assertTrue(second.waitFor(10, TimeUnit.SECONDS));
            for (String name : new String[]{"first", "second"}) {
                String output = Files.readString(directory.resolve(name + ".log"));
                assertTrue(output.contains("LOADED 0.12.0"), output);
                assertFalse(output.contains("Could not extract"), output);
            }
            assertEquals(0, first.exitValue());
            assertEquals(0, second.exitValue());
        } finally {
            if (first.isAlive()) first.destroyForcibly();
            if (second != null && second.isAlive()) second.destroyForcibly();
        }
    }

    private Process start(String name) throws Exception {
        return new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                "-Djava.io.tmpdir=" + directory, "-Djna.library.path=sentinel",
                "-cp", System.getProperty("spark.testRuntimeClasspath"),
                NativeLibraryLoadingTest.class.getName(), directory.toString(), name)
                .redirectErrorStream(true).redirectOutput(directory.resolve(name + ".log").toFile()).start();
    }

    public static void main(String[] args) throws Exception {
        var version = WebviewNative.instance().webview_version();
        if (!"sentinel".equals(System.getProperty("jna.library.path"))) {
            throw new IllegalStateException("Loader changed the global JNA search path");
        }
        System.out.println("LOADED " + version.major + "." + version.minor + "." + version.patch);
        var directory = Path.of(args[0]);
        Files.writeString(directory.resolve(args[1] + ".ready"), "loaded");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(25);
        while (!Files.exists(directory.resolve("release"))) {
            if (System.nanoTime() > deadline) throw new IllegalStateException("Timed out waiting for test release");
            Thread.sleep(25);
        }
    }
}
