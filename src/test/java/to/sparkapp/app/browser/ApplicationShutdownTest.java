package to.sparkapp.app.browser;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Tag("native")
class ApplicationShutdownTest {
    @TempDir Path directory;

    @Test @Timeout(45)
    void normalExitCompletesWithoutForcedTerminationOrRuntimeClassWarning() throws Exception {
        Path log = directory.resolve("shutdown.log");
        var command = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                "-Dspark.nativeTests=true", "-Dspark.dataDir=" + directory.resolve("settings"),
                "-cp", System.getProperty("spark.testRuntimeClasspath"), MainWindowLifecycleProbe.class.getName());
        command.environment().put("WEBVIEW2_USER_DATA_FOLDER", directory.resolve("browser").toString());
        command.redirectErrorStream(true).redirectOutput(log.toFile());
        var process = command.start();
        try {
            assertTrue(process.waitFor(35, TimeUnit.SECONDS), "Application must exit naturally");
            String output = Files.readString(log);
            assertEquals(0, process.exitValue(), output);
            assertTrue(output.contains("NORMAL_SHUTDOWN_REQUESTED"), output);
            assertTrue(output.contains("Webview shutdown complete"), output);
            assertFalse(output.contains("Forcing exit"), output);
            assertFalse(output.contains("Failed to unregister class"), output);
            System.out.println("Full application exit completed naturally without the class-unregister diagnostic.");
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }
}
