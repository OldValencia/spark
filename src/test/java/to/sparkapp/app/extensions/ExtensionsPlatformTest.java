package to.sparkapp.app.extensions;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import to.sparkapp.app.browser.ExtensionPopup;
import to.sparkapp.app.utils.SystemUtils;

class ExtensionsPlatformTest {
    @Test
    void nonWindowsPlatformsRejectExtensionWorkBeforeTouchingTheBrowserOrFiles() {
        var originalOs = System.getProperty("os.name");
        try {
            for (var os : new String[]{"Mac OS X", "Darwin", "Linux", "FreeBSD", ""}) {
                System.setProperty("os.name", os);
                assertFalse(SystemUtils.isWindows(), os);
                var manager = new ExtensionManager();
                assertDoesNotThrow(() -> manager.start(null));
                for (var result : new CompletableFuture<?>[]{manager.refresh(null),
                        manager.importArchive(null, null), manager.setEnabled(null, null, false), manager.remove(null, null)}) {
                    assertTrue(result.isCompletedExceptionally());
                    var error = assertThrows(CompletionException.class, result::join);
                    assertInstanceOf(UnsupportedOperationException.class, error.getCause());
                }
                assertDoesNotThrow(() -> new ExtensionPopup().open(null, null, null, null, null));
            }
        } finally {
            System.setProperty("os.name", originalOs);
        }
    }

    @Test
    void recognizesWindowsVersions() {
        var originalOs = System.getProperty("os.name");
        try {
            for (var os : new String[]{"Windows 10", "Windows 11", "Windows NT", "Windows Server 2025"}) {
                System.setProperty("os.name", os);
                assertTrue(SystemUtils.isWindows(), os);
            }
        } finally {
            System.setProperty("os.name", originalOs);
        }
    }
}
