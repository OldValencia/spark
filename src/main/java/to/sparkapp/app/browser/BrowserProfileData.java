package to.sparkapp.app.browser;

import java.util.function.Consumer;

import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import static to.sparkapp.app.browser.ComCalls.*;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class BrowserProfileData {
    static final String CLEAR_BROWSING_DATA_COMPLETED = "e9710a06-1d1d-49b2-8234-226f35846ae5";

    static void clear(Pointer core, Consumer<Boolean> completed) {
        var extended = query(core, "f75f09a8-667e-4983-88d6-c8773f315e84");
        Pointer profile = null;
        Pointer profile2 = null;

        try {
            var out = new PointerByReference();
            check(call(extended, 105, out));
            profile = out.getValue();
            profile2 = query(profile, "fa740d4b-5eae-4344-a8ad-74be31925397");

            try (var callback = new BrowserStatusCallback(CLEAR_BROWSING_DATA_COMPLETED, completed)) {
                check(call(profile2, 12, callback.pointer()));
            }
        } finally {
            release(profile2);
            release(profile);
            release(extended);
        }
    }
}
