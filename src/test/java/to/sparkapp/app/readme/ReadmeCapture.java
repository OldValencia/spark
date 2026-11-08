package to.sparkapp.app.readme;

import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import java.util.function.Predicate;

import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import javax.imageio.ImageIO;
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
    private static final double SHOT_MARGIN = 32;
    private static final String REJECT_COOKIES = "[...document.querySelectorAll('button')]"
            + ".find(b => /reject non-essential/i.test(b.textContent))?.click()";

    private final Path output;
    private final ReadmeMouse mouse = new ReadmeMouse();
    private MainWindow spark;
    private FxWebViewPane browser;
    private ReadmeBackdrop backdrop;
    private double scale;

    private ReadmeCapture(Path output) throws Exception {
        this.output = output;
    }

    public static void main(String[] args) throws Exception {
        var output = Files.createDirectories(Path.of(args[0]));
        var mode = args.length > 1 ? args[1] : "tour";
        ReadmeProfile.prepare();

        var thread = new Thread(() -> SparkApplication.main(new String[0]), "readme-spark");
        thread.setDaemon(true);
        thread.start();
        ReadmeFx.awaitToolkit();

        var capture = new ReadmeCapture(output);
        try {
            capture.prepare();
            if (!mode.equals("screenshots")) {
                capture.recordTour();
            }
            if (!mode.equals("tour-only")) {
                capture.takeScreenshots();
            }
        } catch (Throwable error) {
            error.printStackTrace();
            Runtime.getRuntime().halt(1);
        }
        System.exit(0);
    }

    private void prepare() throws Exception {
        spark = awaitSpark();
        browser = ReadmeFx.call(ReadmeCapture::findBrowser);
        scale = ReadmeFx.call(() -> Screen.getPrimary().getOutputScaleX());
        backdrop = ReadmeFx.call(ReadmeBackdrop::new);

        ExtensionManager.instance().refresh(browser.extensionBrowser()).get(3, TimeUnit.MINUTES);
        ReadmeFx.pause(3_000);
        reloadSite("Wikipedia");
        reloadSite("ChatGPT");
        browser.extensionBrowser().eval(REJECT_COOKIES);
        ReadmeFx.pause(1_500);
    }

    private void recordTour() throws Exception {
        var region = videoRegion();
        ReadmeFx.run(() -> {
            backdrop.place(region);
            backdrop.caption("01", "Open Spark", "A small window for the sites you reach for.");
            placeSpark(region.getMinX() + 170, region.getMinY(), 860, 700);
        });
        mouse.moveTo(new Point2D(region.getMinX() + 90, region.getMinY() + 110));
        ReadmeFx.pause(800);

        var recorder = new ReadmeRecorder();
        var video = output.resolve("spark-tour.mp4");
        recorder.start(region, scale, video);
        try {
            tour();
        } finally {
            recorder.stop();
        }
        ReadmeRecorder.derive(video, output.resolve("spark-tour.gif"), output.resolve("spark-tour-poster.png"), 2.2);
    }

    private void tour() throws Exception {
        ReadmeFx.pause(2_200);

        chapter("02", "Switch sites", "Each page stays loaded where you left it.");
        mouse.click(ReadmeUi.control("Wikipedia"));
        ReadmeFx.pause(2_000);
        mouse.click(ReadmeUi.control("ChatGPT"));
        ReadmeFx.pause(1_300);
        mouse.click(ReadmeUi.control("Wikipedia"));
        ReadmeFx.pause(1_300);

        chapter("03", "Find a site", "Search, add, and reorder your collection.");
        mouse.click(openMenu("All sites", ReadmeUi.named("Search sites")));
        mouse.type("wiki");
        ReadmeFx.pause(1_400);
        mouse.key(KeyEvent.VK_ESCAPE);
        ReadmeFx.pause(600);

        chapter("04", "Size it your way", "Presets, pinning, and docking at the top.");
        choosePreset("S, ");
        ReadmeFx.pause(1_200);
        choosePreset("M, ");
        ReadmeFx.pause(1_000);

        chapter("05", "Bring your extensions", "Chrome extensions that know which site is open.");
        openFromOptions("Extensions");
        ReadmeFx.pause(1_800);
        mouse.click(ReadmeUi.control("Open Dark Reader"));
        ReadmeFx.pause(3_200);
        mouse.click(ReadmeUi.control("Open Dark Reader"));
        ReadmeFx.pause(900);
        mouse.click(ReadmeUi.control("Close extensions"));
        ReadmeFx.pause(700);

        chapter("06", "Make it yours", "Shortcut, startup, the top-edge pill, and more.");
        openFromOptions("Settings");
        ReadmeFx.pause(1_000);
        mouse.moveTo(ReadmeUi.control("Remember last site"));
        mouse.scroll(6);
        ReadmeFx.pause(900);
        mouse.scroll(-6);
        ReadmeFx.pause(600);
        mouse.click(ReadmeUi.control("Close settings"));
        ReadmeFx.pause(700);

        chapter("07", "Put it away", "Hide it, then come back to the same page.");
        mouse.click(ReadmeUi.control("Hide Spark"));
        ReadmeFx.pause(1_500);
        ReadmeFx.run(spark::showMainWindow);
        ReadmeFx.pause(2_200);
    }

    private void takeScreenshots() throws Exception {
        var screen = ReadmeFx.call(() -> Screen.getPrimary().getVisualBounds());
        var region = new Rectangle2D(screen.getMinX() + (screen.getWidth() - SHOT_WIDTH) / 2,
                screen.getMinY() + (screen.getHeight() - SHOT_HEIGHT) / 2, SHOT_WIDTH, SHOT_HEIGHT);
        ReadmeFx.run(() -> {
            backdrop.caption("", null, null);
            backdrop.place(region);
            placeSpark(region.getMinX() + SHOT_MARGIN, region.getMinY() + SHOT_MARGIN,
                    SHOT_WIDTH - 2 * SHOT_MARGIN, SHOT_HEIGHT - 2 * SHOT_MARGIN);
        });
        mouse.moveTo(new Point2D(region.getMaxX() + 200, region.getMinY() + region.getHeight() / 2));

        mouse.click(ReadmeUi.control("ChatGPT"));
        shoot(region, "spark-main.png");

        openMenu("All sites", ReadmeUi.named("Search sites"));
        shoot(region, "sites.png");
        mouse.click(ReadmeUi.control("Add any website"));
        ReadmeFx.pause(800);
        fillAddSite();
        shoot(region, "add-site.png");
        mouse.click(ReadmeUi.control("Cancel"));
        ReadmeFx.pause(600);

        openMenu("Window size", node -> node.getAccessibleText() != null && node.getAccessibleText().startsWith("S, "));
        shoot(region, "window-size.png");
        mouse.key(KeyEvent.VK_ESCAPE);
        ReadmeFx.pause(500);

        openFromOptions("Settings");
        shoot(region, "settings.png");
        mouse.click(ReadmeUi.control("Close settings"));
        ReadmeFx.pause(500);

        mouse.click(ReadmeUi.control("Wikipedia"));
        ReadmeFx.pause(1_200);
        openFromOptions("Extensions");
        shoot(region, "extensions.png");
        mouse.click(ReadmeUi.control("Open Dark Reader"));
        ReadmeFx.pause(3_000);
        shootWithPopup("Dark Reader", "extension-popup.png");
        mouse.key(KeyEvent.VK_ESCAPE);
        ReadmeFx.pause(500);
        mouse.click(ReadmeUi.control("Close extensions"));
    }

    private void fillAddSite() throws Exception {
        mouse.click(ReadmeUi.control("Site name"));
        mouse.type("DeepL");
        mouse.click(ReadmeUi.control("Website address"));
        mouse.key(KeyEvent.VK_CONTROL, KeyEvent.VK_A);
        mouse.type("https://www.deepl.com");
        ReadmeFx.pause(400);
    }

    private void shoot(Rectangle2D region, String name) throws Exception {
        mouse.moveTo(new Point2D(region.getMaxX() + 200, region.getMinY() + region.getHeight() / 2));
        ReadmeFx.pause(900);
        var pixels = new Rectangle((int) Math.round(region.getMinX() * scale), (int) Math.round(region.getMinY() * scale),
                (int) Math.round(region.getWidth() * scale), (int) Math.round(region.getHeight() * scale));
        var image = new Robot().createScreenCapture(pixels);
        ImageIO.write(image, "png", output.resolve(name).toFile());
    }

    /** Frames Spark together with an extension popover, which may extend beyond the window. */
    private void shootWithPopup(String popupTitle, String name) throws Exception {
        var region = ReadmeFx.call(() -> {
            var popup = Window.getWindows().stream()
                    .filter(window -> window instanceof Stage stage && popupTitle.equals(stage.getTitle()) && stage.isShowing())
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("The extension popover is not open"));
            var minX = Math.min(spark.getX(), popup.getX()) - SHOT_MARGIN;
            var minY = Math.min(spark.getY(), popup.getY()) - SHOT_MARGIN;
            var maxX = Math.max(spark.getX() + spark.getWidth(), popup.getX() + popup.getWidth()) + SHOT_MARGIN;
            var maxY = Math.max(spark.getY() + spark.getHeight(), popup.getY() + popup.getHeight()) + SHOT_MARGIN;
            var framed = new Rectangle2D(minX, minY, maxX - minX, maxY - minY);
            backdrop.place(framed);
            return framed;
        });
        shoot(region, name);
    }

    private void chapter(String number, String heading, String text) throws Exception {
        ReadmeFx.run(() -> backdrop.caption(number, heading, text));
    }

    private void choosePreset(String prefix) throws Exception {
        mouse.click(openMenu("Window size", node -> node.getAccessibleText() != null && node.getAccessibleText().startsWith(prefix)));
        ReadmeFx.pause(400);
    }

    private void openFromOptions(String action) throws Exception {
        mouse.click(openMenu("Window options", ReadmeUi.named(action)));
        ReadmeFx.pause(900);
    }

    /**
     * Clicks a header button until the expected menu item appears. A click can be spent closing a
     * popup that is still open, so it is retried a few times.
     */
    private Point2D openMenu(String button, Predicate<Node> item) throws Exception {
        for (var attempt = 0; attempt < 3; attempt++) {
            mouse.click(ReadmeUi.control(button));
            var found = ReadmeUi.tryCenter(item, 1_500);
            if (found != null) {
                ReadmeFx.pause(400);
                return found;
            }
        }

        return ReadmeUi.center(item);
    }

    private void reloadSite(String name) throws Exception {
        mouse.click(ReadmeUi.control(name));
        ReadmeFx.pause(1_500);
        browser.extensionBrowser().reload();
        ReadmeFx.pause(5_000);
    }

    private void placeSpark(double x, double y, double width, double height) {
        spark.setAlwaysOnTop(true);
        spark.setX(x);
        spark.setY(y);
        spark.setWidth(width);
        spark.setHeight(height);
        spark.toFront();
    }

    private Rectangle2D videoRegion() throws Exception {
        var screen = ReadmeFx.call(() -> Screen.getPrimary().getBounds());
        return new Rectangle2D(screen.getMinX() + (screen.getWidth() - VIDEO_WIDTH) / 2, screen.getMinY(), VIDEO_WIDTH, VIDEO_HEIGHT);
    }

    private static MainWindow awaitSpark() throws Exception {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (System.nanoTime() < deadline) {
            var found = ReadmeFx.call(() -> Window.getWindows().stream()
                    .filter(MainWindow.class::isInstance).map(MainWindow.class::cast)
                    .filter(Stage::isShowing).findFirst().orElse(null));
            if (found != null) {
                ReadmeFx.pause(6_000);
                return found;
            }
            ReadmeFx.pause(200);
        }
        throw new IllegalStateException("The Spark window did not appear");
    }

    private static FxWebViewPane findBrowser() {
        return (FxWebViewPane) ReadmeUi.find(FxWebViewPane.class::isInstance)
                .orElseThrow(() -> new IllegalStateException("The Spark browser pane was not found"));
    }
}
