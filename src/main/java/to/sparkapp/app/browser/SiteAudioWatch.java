package to.sparkapp.app.browser;

import java.util.List;
import java.util.function.Consumer;

/**
 * Tells whether a site plays sound on its main page or on any page it opened. Runs on the owning WebView2 thread.
 */
final class SiteAudioWatch {
    private Consumer<Boolean> changed = playing -> {
    };
    private boolean playing;

    /** Starts reporting, beginning with the current state, so a restarted browser corrects an old value. */
    void observe(List<BrowserContext> contexts, Consumer<Boolean> listener) {
        changed = listener;
        contexts.forEach(context -> watch(context, contexts));
        playing = isPlaying(contexts);
        listener.accept(playing);
    }

    void watch(BrowserContext context, List<BrowserContext> contexts) {
        context.observePlaying(() -> refresh(contexts));
    }

    /** Checks again, for example after a page that played sound was closed. */
    void refresh(List<BrowserContext> contexts) {
        var now = isPlaying(contexts);

        if (now != playing) {
            playing = now;
            changed.accept(now);
        }
    }

    private static boolean isPlaying(List<BrowserContext> contexts) {
        return contexts.stream().anyMatch(BrowserContext::isPlayingAudio);
    }
}
