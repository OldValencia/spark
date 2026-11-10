package to.sparkapp.app.browser;

import static to.sparkapp.app.browser.ComCalls.*;

import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.platform.win32.Guid.GUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Mutes one browsing context and tells whether it plays sound, through ICoreWebView2_8. Runs on the owning WebView2
 * thread.
 */
@Slf4j
@RequiredArgsConstructor
final class BrowserAudio implements AutoCloseable {
    private static final String CORE_8 = "e9632730-6e1e-43ab-b7b8-7b2c9e62e094";
    private static final int PUT_IS_MUTED = 84;
    private static final int ADD_PLAYING_AUDIO_CHANGED = 85;
    private static final int REMOVE_PLAYING_AUDIO_CHANGED = 86;
    private static final int GET_IS_DOCUMENT_PLAYING_AUDIO = 87;
    private static final String PLAYING_AUDIO_CHANGED_HANDLER = "5def109a-2f4b-49fa-b7f6-11c39e513328";
    private static final int E_NOINTERFACE = 0x80004002;

    private final Pointer core;
    private Pointer audio;
    private boolean checked;
    /** A new page plays sound, so a site that is never muted needs no call at all. */
    private boolean muted;

    void setMuted(boolean value) {
        if (muted == value || audio() == null) {
            return;
        }

        int result = call(audio, PUT_IS_MUTED, value ? 1 : 0);

        if (result >= 0) {
            muted = value;
            log.info("Page sound {}", value ? "muted" : "unmuted");
        } else {
            log.warn("Could not change page sound: 0x{}", Integer.toHexString(result));
        }
    }

    /** Calls back whenever the page starts or stops playing sound, whether it is muted or not. */
    void observePlaying(BrowserComEvents events, Runnable changed) {
        if (audio() != null) {
            events.listen(audio, ADD_PLAYING_AUDIO_CHANGED, REMOVE_PLAYING_AUDIO_CHANGED, PLAYING_AUDIO_CHANGED_HANDLER,
                    args -> changed.run());
        }
    }

    /** Whether the page plays sound; a muted page that plays still counts. */
    boolean isPlaying() {
        if (audio() == null) {
            return false;
        }

        var playing = new IntByReference();

        return call(audio, GET_IS_DOCUMENT_PLAYING_AUDIO, playing) >= 0 && playing.getValue() != 0;
    }

    private Pointer audio() {
        if (!checked) {
            checked = true;
            audio = queryAudio();
        }

        return audio;
    }

    private Pointer queryAudio() {
        var out = new PointerByReference();
        int result = call(core, 0, new GUID(CORE_8), out);

        if (result >= 0) {
            return out.getValue();
        }

        if (result == E_NOINTERFACE) {
            log.debug("This WebView2 Runtime cannot mute pages");
        } else {
            log.warn("Page sound interface unavailable: 0x{}", Integer.toHexString(result));
        }

        return null;
    }

    @Override
    public void close() {
        release(audio);
        audio = null;
    }
}
