package to.sparkapp.app.ui.topbar.components;

import javafx.animation.Animation;
import javafx.animation.PauseTransition;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

/**
 * A speaker on a tab's icon while the site plays sound, crossed out when the site is muted. It stays a moment after the
 * sound stops, so short sounds such as notifications do not make it blink.
 */
final class TabSoundBadge extends StackPane {
    static final Duration QUIET_DELAY = Duration.seconds(2);
    /** The speaker is drawn at 18 px and shown at about 12 px, with lines thick enough to read at that size. */
    private static final double SPEAKER_SCALE = 0.66;
    private static final double SPEAKER_STROKE = 2.4;

    private final StackPane playing = speaker("speaker-wave");
    private final StackPane muted = speaker("speaker-x-mark");
    private final PauseTransition quiet = new PauseTransition(QUIET_DELAY);

    TabSoundBadge() {
        getChildren().addAll(new Circle(6.8, Color.web("#25262a")), playing, muted);
        setManaged(false);
        setVisible(false);
        setMouseTransparent(true);
        resize(18, 18);
        quiet.setOnFinished(event -> setVisible(false));
    }

    private static StackPane speaker(String icon) {
        var speaker = ShellIcon.create(icon, Color.WHITE, SPEAKER_STROKE);
        speaker.setScaleX(SPEAKER_SCALE);
        speaker.setScaleY(SPEAKER_SCALE);

        return speaker;
    }

    /** Shows the speaker at once when sound starts, and hides it after a moment of quiet. */
    void show(boolean sounding, boolean mute) {
        playing.setVisible(!mute);
        muted.setVisible(mute);

        if (sounding) {
            quiet.stop();
            setVisible(true);
        } else if (isVisible() && quiet.getStatus() != Animation.Status.RUNNING) {
            quiet.playFromStart();
        }
    }

    boolean isShowingMuted() {
        return isVisible() && muted.isVisible();
    }

    boolean isShowingSound() {
        return isVisible() && playing.isVisible();
    }
}
