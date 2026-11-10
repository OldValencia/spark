package to.sparkapp.app.ui.topbar.components;

import java.util.function.Consumer;

import javafx.util.Duration;
import to.sparkapp.app.utils.FxTimer;

/**
 * Tells lasting sound from short sounds: the mascot starts listening once a site has played for a moment, and stops
 * after a moment of quiet, so a notification or a pause between songs does not make it start and stop.
 */
final class MascotHearing {
    static final Duration START_AFTER = Duration.millis(1_500);
    static final Duration STOP_AFTER = Duration.seconds(2);

    private final Consumer<Boolean> listening;
    private final FxTimer start;
    private final FxTimer stop;
    private boolean heard;

    MascotHearing(Consumer<Boolean> listening) {
        this.listening = listening;
        start = FxTimer.once(START_AFTER, () -> changed(true));
        stop = FxTimer.once(STOP_AFTER, () -> changed(false));
    }

    void setSound(boolean playing) {
        if (playing) {
            stop.stop();

            if (!heard) {
                start.start();
            }
        } else {
            start.stop();

            if (heard) {
                stop.start();
            }
        }
    }

    void dispose() {
        start.stop();
        stop.stop();
    }

    private void changed(boolean playing) {
        heard = playing;
        listening.accept(playing);
    }
}
