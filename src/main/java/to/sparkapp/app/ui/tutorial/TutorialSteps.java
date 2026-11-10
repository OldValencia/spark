package to.sparkapp.app.ui.tutorial;

import java.util.List;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.ui.topbar.TopBarArea;
import to.sparkapp.app.utils.GlobalHotkeyManager;

/** The tour's text. The shortcut comes last, because setting it opens Settings. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TutorialSteps {
    private static final String NEXT = "Next";
    private static final String SKIP = "Skip tutorial";
    private static final String DONE = "Done";

    static List<TutorialStep> create(TopBarArea bar, AppPreferences preferences) {
        return List.of(
                new TutorialStep(bar.getSitesButton(), "Your sites",
                        "Hi, I'm Sparky. All your sites live here: add a new one, search, or drag a site to reorder.",
                        NEXT, SKIP, false),
                new TutorialStep(bar.getSizeButton(), "Window",
                        "Pick a size, keep Spark above other apps, or dock it at the top of the screen.", NEXT,
                        SKIP, false),
                shortcutStep(bar, preferences));
    }

    private static TutorialStep shortcutStep(TopBarArea bar, AppPreferences preferences) {
        var keys = preferences.getHotkeyToStartApplication();
        var title = "Open Spark from anywhere";

        if (keys.isEmpty()) {
            return new TutorialStep(bar.getOptionsButton(), title,
                    "Choose a shortcut to show and hide Spark from any app. A spare mouse button works too.",
                    "Set shortcut", DONE, true);
        }

        return new TutorialStep(bar.getOptionsButton(), title,
                "Press " + GlobalHotkeyManager.getHotkeyText(keys) +
                        " to show and hide Spark from any app. You can change it in Settings.", DONE, null, false);
    }
}
