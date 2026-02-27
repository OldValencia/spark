package to.sparkapp.app.browser;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import to.sparkapp.app.utils.SystemUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

interface WebviewNative extends Library {

    WebviewNative WEBVIEW_NATIVE = SystemUtils.isMac() ? null : runSetup();

    private static WebviewNative runSetup() {
        var osName = System.getProperty("os.name").toLowerCase();
        var osArch = System.getProperty("os.arch").toLowerCase();
        String libPath;

        if (osName.contains("win")) {
            libPath = "/webview/natives/x86_64/windows_nt/webview.dll";
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
        void callback(long seq, String req, long arg);
    }

    interface DispatchCallback extends Callback {
        void callback(long pointer, long arg);
    }

    long webview_create(boolean debug, Pointer window);

    long webview_get_window(long pointer);

    void webview_set_html(long pointer, String html);

    void webview_navigate(long pointer, String url);

    void webview_set_title(long pointer, String title);

    void webview_set_size(long pointer, int width, int height, int hint);

    void webview_run(long pointer);

    void webview_destroy(long pointer);

    void webview_terminate(long pointer);

    void webview_eval(long pointer, String js);

    void webview_init(long pointer, String js);

    void webview_bind(long pointer, String name, BindCallback callback, long arg);

    void webview_unbind(long pointer, String name);

    void webview_return(long pointer, long seq, boolean isError, String result);

    void webview_dispatch(long pointer, DispatchCallback callback, long arg);

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
