package to.sparkapp.app.readme;

import java.awt.event.KeyEvent;
import java.nio.file.Path;

import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import to.sparkapp.app.windows.MainWindow;

/** Records the README video: one short chapter per thing Spark does, with a caption on the backdrop. */
final class ReadmeTour {
    private static final double POSTER_SECOND = 2.2;

    private final ReadmeShell shell;
    private final ReadmeMouse mouse;
    private final ReadmeBackdrop backdrop;
    private final MainWindow spark;

    ReadmeTour(ReadmeShell shell, ReadmeMouse mouse, ReadmeBackdrop backdrop, MainWindow spark) {
        this.shell = shell;
        this.mouse = mouse;
        this.backdrop = backdrop;
        this.spark = spark;
    }

    void record(Rectangle2D region, double scale, Path output) throws Exception {
        ReadmeFx.run(() -> {
            backdrop.place(region);
            backdrop.caption("01", "Open Spark", "A small window for the sites you reach for.");
            shell.place(region.getMinX() + 170, region.getMinY(), 860, 700);
        });
        mouse.moveTo(new Point2D(region.getMinX() + 90, region.getMinY() + 110));
        ReadmeFx.pause(800);

        var recorder = new ReadmeRecorder();
        var video = output.resolve("spark-tour.mp4");
        recorder.start(region, scale, video);

        try {
            play();
        } finally {
            recorder.stop();
        }

        ReadmeRecorder.derive(video, output.resolve("spark-tour.gif"), output.resolve("spark-tour-poster.png"),
                POSTER_SECOND);
    }

    private void play() throws Exception {
        ReadmeFx.pause(2_200);
        switchSites();
        findSite();
        openTabMenu();
        leaveSite();
        sizeWindow();
        openExtension();
        openSettings();
        putAway();
    }

    private void switchSites() throws Exception {
        chapter("02", "Switch sites", "Each page stays loaded where you left it.");
        shell.openSite("Wikipedia");
        ReadmeFx.pause(2_000);
        shell.openSite("ChatGPT");
        ReadmeFx.pause(1_300);
        shell.openSite("Wikipedia");
        ReadmeFx.pause(1_300);
    }

    private void findSite() throws Exception {
        chapter("03", "Find a site", "Ctrl + Shift + L, even inside a page.");
        ReadmeFx.pause(600);
        shell.findSite();
        mouse.type("wiki");
        ReadmeFx.pause(1_400);
        mouse.key(KeyEvent.VK_ESCAPE);
        ReadmeFx.pause(600);
    }

    private void openTabMenu() throws Exception {
        chapter("04", "A menu on every tab", "Right-click a tab for zoom, sound, Mobile version and more.");
        shell.openTabMenu("ChatGPT");
        ReadmeFx.pause(1_200);
        mouse.moveTo(ReadmeUi.center(ReadmeShell.startingWith("Mobile version")));
        ReadmeFx.pause(900);
        mouse.moveTo(ReadmeUi.center(ReadmeShell.startingWith("Mute site")));
        ReadmeFx.pause(900);
        mouse.key(KeyEvent.VK_ESCAPE);
        ReadmeFx.pause(600);
    }

    private void leaveSite() throws Exception {
        chapter("05", "Wander off, come back", "A bar shows where you are. The cross takes you back.");
        shell.leaveSite("Wikipedia");
        shell.returnToSite("Wikipedia");
    }

    private void sizeWindow() throws Exception {
        chapter("06", "Size it your way", "Presets, docking, and a size for each site.");
        mouse.moveTo(shell.openMenu("Window size", ReadmeShell.startingWith("Remember size for")));
        ReadmeFx.pause(1_200);
        mouse.click(ReadmeUi.center(ReadmeShell.startingWith("S, ")));
        ReadmeFx.pause(1_400);
        shell.choosePreset("M, ");
        ReadmeFx.pause(1_000);
    }

    private void openExtension() throws Exception {
        chapter("07", "Bring your extensions", "Chrome extensions that know which site is open.");
        shell.openFromOptions("Extensions");
        ReadmeFx.pause(1_800);
        mouse.click(ReadmeUi.control("Open Dark Reader"));
        ReadmeFx.pause(3_200);
        mouse.click(ReadmeUi.control("Open Dark Reader"));
        ReadmeFx.pause(900);
        mouse.click(ReadmeUi.control("Close extensions"));
        ReadmeFx.pause(700);
    }

    private void openSettings() throws Exception {
        chapter("08", "Make it yours", "Shortcut, startup, sound, the top-edge pill, and more.");
        shell.openFromOptions("Settings");
        ReadmeFx.pause(1_000);
        mouse.moveTo(ReadmeUi.control("Remember last site"));
        mouse.scroll(6);
        ReadmeFx.pause(900);
        mouse.scroll(-6);
        ReadmeFx.pause(600);
        mouse.click(ReadmeUi.control("Close settings"));
        ReadmeFx.pause(700);
    }

    private void putAway() throws Exception {
        chapter("09", "Put it away", "Hide it, then come back to the same page.");
        mouse.click(ReadmeUi.control("Hide Spark"));
        ReadmeFx.pause(1_500);
        ReadmeFx.run(spark::showMainWindow);
        ReadmeFx.pause(2_200);
    }

    private void chapter(String number, String heading, String text) throws Exception {
        ReadmeFx.run(() -> backdrop.caption(number, heading, text));
    }
}
