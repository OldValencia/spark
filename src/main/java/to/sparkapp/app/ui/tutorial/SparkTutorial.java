package to.sparkapp.app.ui.tutorial;

import java.util.List;
import java.util.function.Supplier;

import javafx.beans.InvalidationListener;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.stage.Screen;
import javafx.stage.Stage;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.ui.topbar.TopBarArea;
import to.sparkapp.app.ui.topbar.components.MascotGuide;
import to.sparkapp.app.ui.topbar.components.SparkMascot;

/**
 * A three-step first-run tour. The mascot leaves the bar, flies to each bubble and looks at the
 * control it explains; finishing or skipping the tour is remembered.
 */
public final class SparkTutorial {
    private static final double GUIDE_GAP = 6;
    private static final double GUIDE_DROP = 10;

    private final Stage window;
    private final AppPreferences preferences;
    private final Supplier<TopBarArea> bar;
    private final Runnable openShortcutSettings;
    private final TutorialBubble bubble = new TutorialBubble();
    private final MascotGuide guide;
    private final InvalidationListener follow = observable -> follow();
    private List<TutorialStep> steps = List.of();
    private SparkMascot mascot;
    private int index = -1;

    public SparkTutorial(Stage window, AppPreferences preferences, Supplier<TopBarArea> bar,
                         Runnable openShortcutSettings) {
        this.window = window;
        this.preferences = preferences;
        this.bar = bar;
        this.openShortcutSettings = openShortcutSettings;
        guide = new MascotGuide(preferences::isReducedMotion);
    }

    public boolean isRunning() {
        return index >= 0;
    }

    int currentStep() {
        return index;
    }

    TutorialBubble bubble() {
        return bubble;
    }

    MascotGuide guide() {
        return guide;
    }

    public void startIfPending() {
        if (preferences.isTutorialPending()) {
            start();
        }
    }

    public void start() {
        var topBar = bar.get();

        if (isRunning() || topBar == null || !window.isShowing()) {
            return;
        }

        steps = TutorialSteps.create(topBar, preferences);
        mascot = topBar.getMascot().canGuide() ? topBar.getMascot() : null;
        window.xProperty().addListener(follow);
        window.yProperty().addListener(follow);
        window.widthProperty().addListener(follow);

        if (mascot != null) {
            guide.appear(window, mascot.screenPosition());
            mascot.setAway(true);
        }

        show(0);
    }

    /** Closes the tour without remembering it, for example when Spark is hidden midway. */
    public void cancel() {
        if (!isRunning()) {
            return;
        }

        stop();
        guide.dismiss();
        returnMascot();
    }

    private void show(int step) {
        index = step;
        var current = steps.get(step);
        var card = bubble.present(window, current, step + 1, steps.size(), this::primary, this::finish);
        guideTo(card, current, true);
    }

    private void primary() {
        if (index < steps.size() - 1) {
            show(index + 1);

            return;
        }

        var opensShortcut = steps.get(index).opensShortcut();
        finish();

        if (opensShortcut) {
            openShortcutSettings.run();
        }
    }

    /** Finishing and skipping both mean the user has seen enough. */
    private void finish() {
        preferences.setTutorialDone(true);
        stop();

        if (mascot != null) {
            guide.flyTo(mascot.screenPosition(), null, () -> {
                guide.dismiss();
                returnMascot();
            });
        } else {
            guide.dismiss();
        }
    }

    private void stop() {
        index = -1;
        bubble.hide();
        window.xProperty().removeListener(follow);
        window.yProperty().removeListener(follow);
        window.widthProperty().removeListener(follow);
    }

    private void returnMascot() {
        if (mascot != null) {
            mascot.setAway(false);
            mascot = null;
        }
    }

    private void follow() {
        if (!isRunning()) {
            return;
        }

        var current = steps.get(index);
        guideTo(bubble.place(window, current), current, false);
    }

    private void guideTo(Bounds card, TutorialStep step, boolean fly) {
        if (mascot == null) {
            return;
        }

        var spot = guideSpot(card);
        var target = step.target().localToScreen(step.target().getBoundsInLocal());
        var lookAt = target == null ? null : new Point2D(target.getCenterX(), target.getCenterY());

        if (fly) {
            guide.flyTo(spot, lookAt, null);
        } else {
            guide.place(spot);
            guide.lookAt(lookAt);
        }
    }

    /** Next to the bubble's left edge, or its right edge when the screen ends on the left. */
    private Point2D guideSpot(Bounds card) {
        var screen = Screen.getScreensForRectangle(card.getMinX(), card.getMinY(), 1, 1).stream().findFirst()
                .orElse(Screen.getPrimary()).getVisualBounds();
        var left = card.getMinX() - MascotGuide.WIDTH - GUIDE_GAP;
        var x = left >= screen.getMinX() ? left : card.getMaxX() + GUIDE_GAP;

        return new Point2D(x, card.getMinY() + GUIDE_DROP);
    }
}
