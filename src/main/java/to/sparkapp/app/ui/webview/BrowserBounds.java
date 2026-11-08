package to.sparkapp.app.ui.webview;


import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import to.sparkapp.app.utils.NativeWindowUtils;
import to.sparkapp.app.utils.SystemUtils;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class BrowserBounds {
    static int[] calculate(StackPane host, long parentHandle) {
        if (host.getScene() == null || host.getScene().getWindow() == null) {
            return new int[]{0, 0, (int) Math.round(host.getWidth()), (int) Math.round(host.getHeight())};
        }

        var window = host.getScene().getWindow();
        var scene = host.getScene();
        var bounds = host.localToScene(host.getBoundsInLocal());

        if (bounds == null || bounds.getWidth() <= 0 || bounds.getHeight() <= 0) {
            return new int[]{0, 0, (int) Math.round(host.getWidth()), (int) Math.round(host.getHeight())};
        }

        int x;
        int y;
        int w;
        int h;

        if (SystemUtils.isWindows()) {
            var scale = resolveHostScale(window, parentHandle);
            var sx = scale[0];
            var sy = scale[1];
            x = (int) Math.round(bounds.getMinX() * sx);
            y = (int) Math.round(bounds.getMinY() * sy);
            w = (int) Math.round(bounds.getWidth() * sx);
            h = (int) Math.round(bounds.getHeight() * sy);

            // Never let the webview stick out of the host window's client area.
            int[] client = NativeWindowUtils.getClientSize(parentHandle);

            if (client != null && client[0] > 0 && client[1] > 0) {
                w = Math.min(w, client[0] - x);
                h = Math.min(h, client[1] - y);
            }
        } else {
            x = (int) Math.round(bounds.getMinX() + scene.getX());
            y = (int) Math.round(bounds.getMinY() + scene.getY());
            w = (int) Math.round(bounds.getWidth());
            h = (int) Math.round(bounds.getHeight());
        }

        return new int[]{x, y, w, h};
    }

    private static double[] resolveHostScale(Window window, long parentHandle) {
        var sx = window.getOutputScaleX();
        var sy = window.getOutputScaleY();

        int[] client = NativeWindowUtils.getClientSize(parentHandle);

        if (client == null || window.getWidth() <= 0 || window.getHeight() <= 0) {
            return new double[]{sx, sy};
        }

        var realX = client[0] / window.getWidth();
        var realY = client[1] / window.getHeight();

        if (isSaneScale(realX) && isSaneScale(realY) && (Math.abs(realX - sx) > 0.01 || Math.abs(realY - sy) > 0.01)) {
            return new double[]{realX, realY};
        }

        return new double[]{sx, sy};
    }

    private static boolean isSaneScale(double scale) {
        return scale >= 0.5 && scale <= 8.0;
    }

    static long resolveParentHandle(Window window) {
        return window instanceof Stage stage ? NativeWindowUtils.windowHandle(stage) : 0L;
    }
}
