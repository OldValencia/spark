package to.sparkapp.app.readme;

import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.nio.file.Path;

import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.stage.Stage;
import javafx.stage.Window;
import javax.imageio.ImageIO;
import to.sparkapp.app.windows.MainWindow;

/** Takes the README screenshots of Spark on the plain backdrop, without captions. */
final class ReadmeScreenshots {
    private static final double MARGIN = 32;

    private final ReadmeShell shell;
    private final ReadmeMouse mouse;
    private final ReadmeBackdrop backdrop;
    private final MainWindow spark;
    private final double scale;
    private final Path output;

    ReadmeScreenshots(ReadmeShell shell, ReadmeMouse mouse, ReadmeBackdrop backdrop, MainWindow spark, double scale,
                      Path output) {
        this.shell = shell;
        this.mouse = mouse;
        this.backdrop = backdrop;
        this.spark = spark;
        this.scale = scale;
        this.output = output;
    }

    void take(Rectangle2D region) throws Exception {
        ReadmeFx.run(() -> {
            backdrop.caption("", null, null);
            backdrop.place(region);
        });

        openSite("ChatGPT", region);
        shoot(region, "spark-main.png");
        shootSites(region);
        shootWindowSize(region);
        shootTabMenu(region);
        shootOffSite(region);
        shootSettings(region);
        shootExtensions(region);
    }

    /**
     * Shows a site and fits Spark into the frame again. A site switch gives the window the size shared by the
     * sites, which the capture's own placement does not change.
     */
    private void openSite(String name, Rectangle2D region) throws Exception {
        shell.openSite(name);
        ReadmeFx.pause(1_200);
        ReadmeFx.run(() -> shell.place(region.getMinX() + MARGIN, region.getMinY() + MARGIN,
                region.getWidth() - 2 * MARGIN, region.getHeight() - 2 * MARGIN));
        ReadmeFx.pause(400);
    }

    private void shootSites(Rectangle2D region) throws Exception {
        shell.openMenu("All sites", ReadmeUi.named("Search sites"));
        shoot(region, "sites.png");
        mouse.click(ReadmeUi.control("Add any website"));
        ReadmeFx.pause(800);

        mouse.click(ReadmeUi.control("Site name"));
        mouse.type("DeepL");
        mouse.click(ReadmeUi.control("Website address"));
        mouse.key(KeyEvent.VK_CONTROL, KeyEvent.VK_A);
        mouse.type("https://www.deepl.com");
        ReadmeFx.pause(400);
        shoot(region, "add-site.png");

        mouse.click(ReadmeUi.control("Cancel"));
        ReadmeFx.pause(600);
    }

    private void shootWindowSize(Rectangle2D region) throws Exception {
        shell.openMenu("Window size", ReadmeShell.startingWith("Remember size for"));
        shoot(region, "window-size.png");
        shell.closeMenus();
    }

    private void shootTabMenu(Rectangle2D region) throws Exception {
        openSite("Wikipedia", region);
        shell.openTabMenu("Wikipedia");
        shoot(region, "tab-menu.png");
        shell.closeMenus();
    }

    private void shootOffSite(Rectangle2D region) throws Exception {
        shell.leaveSite("Wikipedia");
        shoot(region, "off-site.png");
        shell.returnToSite("Wikipedia");
    }

    private void shootSettings(Rectangle2D region) throws Exception {
        shell.openFromOptions("Settings");
        shoot(region, "settings.png");
        mouse.click(ReadmeUi.control("Close settings"));
        ReadmeFx.pause(500);
    }

    private void shootExtensions(Rectangle2D region) throws Exception {
        shell.openFromOptions("Extensions");
        shoot(region, "extensions.png");
        mouse.click(ReadmeUi.control("Open Dark Reader"));
        ReadmeFx.pause(3_000);
        shoot(framedWithPopup("Dark Reader"), "extension-popup.png");
        shell.closeMenus();
        mouse.click(ReadmeUi.control("Close extensions"));
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
    private Rectangle2D framedWithPopup(String popupTitle) throws Exception {
        return ReadmeFx.call(() -> {
            var popup = Window.getWindows().stream()
                    .filter(window -> isShowingStage(window, popupTitle))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("The extension popover is not open"));

            var minX = Math.min(spark.getX(), popup.getX()) - MARGIN;
            var minY = Math.min(spark.getY(), popup.getY()) - MARGIN;
            var maxX = Math.max(spark.getX() + spark.getWidth(), popup.getX() + popup.getWidth()) + MARGIN;
            var maxY = Math.max(spark.getY() + spark.getHeight(), popup.getY() + popup.getHeight()) + MARGIN;
            var framed = new Rectangle2D(minX, minY, maxX - minX, maxY - minY);

            backdrop.place(framed);
            return framed;
        });
    }

    private static boolean isShowingStage(Window window, String title) {
        return window instanceof Stage stage && title.equals(stage.getTitle()) && stage.isShowing();
    }
}
