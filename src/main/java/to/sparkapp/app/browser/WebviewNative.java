package to.sparkapp.app.browser;

import com.sun.jna.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * JNA Wrapper for the underlying C/C++ webview library.
 */
interface WebviewNative extends Library {

    static WebviewNative instance() {
        return Holder.INSTANCE;
    }

    final class Holder {
        private static final WebviewNative INSTANCE = runSetup();
        private Holder() {}
    }

    private static WebviewNative runSetup() {
        var osName = System.getProperty("os.name").toLowerCase();
        var osArch = System.getProperty("os.arch").toLowerCase();
        String libPath;

        if (osName.contains("win")) {
            libPath = "/webview/natives/x86_64/windows_nt/webview.dll";
        } else if (osName.contains("mac")) {
            boolean isArm = osArch.contains("aarch64") || osArch.contains("arm");
            libPath = "/webview/natives/" + (isArm ? "aarch64" : "x86_64") + "/macos/libwebview.dylib";
        } else {
            libPath = "/webview/natives/x86_64/linux/gnu/libwebview.so";
        }

        try {
            var tempDir = new File(System.getProperty("java.io.tmpdir"), "spark_webview_natives");
            if (!tempDir.exists()) tempDir.mkdirs();

            var targetFile = new File(tempDir, new File(libPath).getName());
            targetFile.deleteOnExit();

            try (var in = WebviewNative.class.getResourceAsStream(libPath)) {
                if (in == null) {
                    throw new RuntimeException("Native library not found in resources: " + libPath);
                }
                Files.copy(in, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                if (!e.getMessage().contains("used by another process")) {
                    System.err.println("Warning: Could not extract native library. " + e.getMessage());
                }
            }

            System.setProperty("jna.library.path", tempDir.getAbsolutePath());
            System.load(targetFile.getAbsolutePath());

            return Native.load(
                    "webview",
                    WebviewNative.class,
                    Collections.singletonMap(Library.OPTION_STRING_ENCODING, "UTF-8")
            );

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Webview native library", e);
        }
    }

    int WV_HINT_NONE = 0;
    int WV_HINT_MIN = 1;
    int WV_HINT_MAX = 2;
    int WV_HINT_FIXED = 3;

    interface BindCallback extends Callback {
        void callback(String seq, String req, Pointer arg);
    }

    interface DispatchCallback extends Callback {
        void callback(Pointer pointer, Pointer arg);
    }

    Pointer webview_create(int debug, Pointer window);

    Pointer webview_get_window(Pointer pointer);

    Pointer webview_get_native_handle(Pointer pointer, int kind);

    int webview_set_html(Pointer pointer, String html);

    int webview_navigate(Pointer pointer, String url);

    int webview_set_title(Pointer pointer, String title);

    int webview_set_size(Pointer pointer, int width, int height, int hint);

    int webview_run(Pointer pointer);

    int webview_destroy(Pointer pointer);

    int webview_terminate(Pointer pointer);

    int webview_eval(Pointer pointer, String js);

    int webview_init(Pointer pointer, String js);

    int webview_bind(Pointer pointer, String name, BindCallback callback, Pointer arg);

    int webview_unbind(Pointer pointer, String name);

    int webview_return(Pointer pointer, String seq, int status, String result);

    int webview_dispatch(Pointer pointer, DispatchCallback callback, Pointer arg);

    VersionInfoStruct webview_version();

    class VersionInfoStruct extends Structure {
        public int major, minor, patch;
        public byte[] version_number = new byte[32];
        public byte[] pre_release = new byte[48];
        public byte[] build_metadata = new byte[48];

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("major", "minor", "patch", "version_number", "pre_release", "build_metadata");
        }
    }
}
