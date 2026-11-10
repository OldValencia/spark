package to.sparkapp.app.readme;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import to.sparkapp.app.SparkApplication;
import to.sparkapp.app.extensions.ExtensionManager;
import to.sparkapp.app.ui.webview.FxWebViewPane;
import to.sparkapp.app.windows.MainWindow;

/**
 * Records the README tour and screenshots from a real Spark instance with a disposable profile.
 * It moves the real mouse, so the machine must not be used while it runs.
 */
public final class ReadmeCapture {
    private static final double VIDEO_WIDTH = 1200;
    private static final double VIDEO_HEIGHT = 900;
    private static final double SHOT_WIDTH = 1164;
    private static final double SHOT_HEIGHT = 804;

    private ReadmeCapture() {
    }

    public static void main(String[] args) throws Exception {
        var output = Files.createDirectories(Path.of(args[0]));
        var mode = args.length > 1 ? args[1] : "tour";
        ReadmeProfile.prepare();

        var thread = new Thread(() -> SparkApplication.main(new String[0]), "readme-spark");
        thread.setDaemon(true);
        thread.start();
        ReadmeFx.awaitToolkit();

        try {
            capture(output, mode);
        } catch (Throwable error) {
            error.printStackTrace();
            Runtime.getRuntime().halt(1);
        }

        System.exit(0);
    }

    private static void capture(Path output, String mode) throws Exception {
        var spark = awaitSpark();
        var browser = ReadmeFx.call(ReadmeCapture::findBrowser);
        var scale = ReadmeFx.call(() -> Screen.getPrimary().getOutputScaleX());
        var backdrop = ReadmeFx.call(ReadmeBackdrop::new);
        var mouse = new ReadmeMouse();
        var shell = new ReadmeShell(mouse, spark, browser);

        ExtensionManager.instance().refresh(browser.extensionBrowser()).get(3, TimeUnit.MINUTES);
        ReadmeFx.pause(3_000);
        shell.prepareSites();

        if (!mode.equals("screenshots")) {
            new ReadmeTour(shell, mouse, backdrop, spark).record(videoRegion(), scale, output);
        }

        if (!mode.equals("tour-only")) {
            new ReadmeScreenshots(shell, mouse, backdrop, spark, scale, output).take(shotRegion());
        }
    }

    private static Rectangle2D videoRegion() throws Exception {
        var screen = ReadmeFx.call(() -> Screen.getPrimary().getBounds());

        return new Rectangle2D(screen.getMinX() + (screen.getWidth() - VIDEO_WIDTH) / 2, screen.getMinY(),
                VIDEO_WIDTH, VIDEO_HEIGHT);
    }

    private static Rectangle2D shotRegion() throws Exception {
        var screen = ReadmeFx.call(() -> Screen.getPrimary().getVisualBounds());

        return new Rectangle2D(screen.getMinX() + (screen.getWidth() - SHOT_WIDTH) / 2,
                screen.getMinY() + (screen.getHeight() - SHOT_HEIGHT) / 2, SHOT_WIDTH, SHOT_HEIGHT);
    }

    private static MainWindow awaitSpark() throws Exception {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);

        while (System.nanoTime() < deadline) {
            var found = ReadmeFx.call(ReadmeCapture::showingSpark);

            if (found != null) {
                ReadmeFx.pause(6_000);
                return found;
            }

            ReadmeFx.pause(200);
        }

        throw new IllegalStateException("The Spark window did not appear");
    }

    private static MainWindow showingSpark() {
        return Window.getWindows().stream()
                .filter(MainWindow.class::isInstance)
                .map(MainWindow.class::cast)
                .filter(Stage::isShowing)
                .findFirst()
                .orElse(null);
    }

    private static FxWebViewPane findBrowser() {
        return (FxWebViewPane) ReadmeUi.find(FxWebViewPane.class::isInstance)
                .orElseThrow(() -> new IllegalStateException("The Spark browser pane was not found"));
    }
}
