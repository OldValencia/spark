package to.sparkapp.app.browser;

import com.sun.jna.*;

import java.nio.file.Path;
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

        private Holder() {
        }
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
            byte[] library;

            try (var in = WebviewNative.class.getResourceAsStream(libPath)) {
                if (in == null) {
                    throw new IllegalStateException("Native library not found in resources: " + libPath);
                }

                library = in.readAllBytes();
            }

            var cacheRoot = Path.of(System.getProperty("java.io.tmpdir"), "spark_webview_natives");
            var relativeLibrary = Path.of(libPath.substring("/webview/natives/".length()));
            var target = NativeLibraryCache.extract(cacheRoot, relativeLibrary, library);

            return Native.load(target.toString(), WebviewNative.class,
                    Collections.singletonMap(Library.OPTION_STRING_ENCODING, "UTF-8"));

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
        public int major;
        public int minor;
        public int patch;

        public byte[] version_number = new byte[32];
        public byte[] pre_release = new byte[48];
        public byte[] build_metadata = new byte[48];

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("major", "minor", "patch", "version_number", "pre_release", "build_metadata");
        }
    }
}
