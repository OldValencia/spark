package to.sparkapp.app.readme;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javafx.geometry.Rectangle2D;

/** Records a screen region with FFmpeg's GDI capture: video only, with the pointer visible. */
final class ReadmeRecorder {
    private Process process;

    void start(Rectangle2D region, double scale, Path video) throws IOException {
        var command = List.of("ffmpeg", "-y", "-loglevel", "error",
                "-f", "gdigrab", "-framerate", "30", "-draw_mouse", "1",
                "-offset_x", String.valueOf(Math.round(region.getMinX() * scale)),
                "-offset_y", String.valueOf(Math.round(region.getMinY() * scale)),
                "-video_size", Math.round(region.getWidth() * scale) + "x" + Math.round(region.getHeight() * scale),
                "-i", "desktop", "-an",
                "-vf", "scale=" + Math.round(region.getWidth()) + ":" + Math.round(region.getHeight()) + ":flags=lanczos",
                "-c:v", "libx264", "-preset", "slow", "-crf", "24", "-pix_fmt", "yuv420p", "-movflags", "+faststart",
                video.toString());
        process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.INHERIT).start();
        ReadmeFx.pause(1_200);
    }

    /** Asks FFmpeg to finish the file as if q were pressed. */
    void stop() throws Exception {
        try (var input = process.getOutputStream()) {
            input.write("q".getBytes(StandardCharsets.US_ASCII));
        }
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("FFmpeg did not finish the recording");
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("FFmpeg failed with exit code " + process.exitValue());
        }
    }

    /** Builds the README GIF and poster frame from the recorded MP4, as documented in docs/media. */
    static void derive(Path video, Path gif, Path poster, double posterSecond) throws Exception {
        run(List.of("ffmpeg", "-y", "-loglevel", "error", "-i", video.toString(), "-vf",
                "fps=12,scale=960:-1:flags=lanczos,split[a][b];[a]palettegen=max_colors=128:stats_mode=diff[p];"
                        + "[b][p]paletteuse=dither=bayer:bayer_scale=3:diff_mode=rectangle",
                "-loop", "0", gif.toString()));
        run(List.of("ffmpeg", "-y", "-loglevel", "error", "-ss", String.valueOf(posterSecond), "-i", video.toString(),
                "-frames:v", "1", poster.toString()));
    }

    private static void run(List<String> command) throws Exception {
        var process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.INHERIT).start();
        if (process.waitFor() != 0) {
            throw new IllegalStateException("FFmpeg failed: " + String.join(" ", command));
        }
    }
}
