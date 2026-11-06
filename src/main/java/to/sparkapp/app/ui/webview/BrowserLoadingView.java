package to.sparkapp.app.ui.webview;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import javafx.animation.PauseTransition;
import javafx.util.Duration;
import lombok.Getter;
import to.sparkapp.app.browser.WebviewManager;

final class BrowserLoadingView {
    private final Supplier<WebviewManager> browser;
    private final BooleanSupplier visible;
    private final Runnable syncBounds;
    private final PauseTransition loadTimeout = new PauseTransition(Duration.seconds(20));
    @Getter
    private final WebViewLoadingOverlay overlay = new WebViewLoadingOverlay();

    BrowserLoadingView(Supplier<WebviewManager> browser, BooleanSupplier visible, Runnable syncBounds) {
        this.browser = browser;
        this.visible = visible;
        this.syncBounds = syncBounds;
    }

    void refresh() {
        loadTimeout.stop();

        if (!visible.getAsBoolean()) {
            return;
        }

        var active = browser.get();

        if (active.getLoadError() != null) {
            active.setVisible(false);
            overlay.showError(active.getLoadError(), () -> browser.get().reload());
        } else if (active.isLoading()) {
            active.setVisible(false);
            overlay.activate(null, 0, null);
            loadTimeout.setOnFinished(event -> showTimeout());
            loadTimeout.playFromStart();
        } else {
            overlay.deactivate();
            syncBounds.run();
            showPage();
        }
    }

    private void showTimeout() {
        if (!visible.getAsBoolean() || !browser.get().isLoading()) {
            return;
        }

        overlay.showError("Loading is taking longer than expected. You can retry or show the page.",
                () -> browser.get().reload(), this::showPage);
    }

    private void showPage() {
        overlay.deactivate();
        browser.get().setVisible(true);
        browser.get().focusBrowser();
    }

    void pause() {
        overlay.pauseAnimation();
        loadTimeout.stop();
    }

    void stop() {
        overlay.deactivate();
        loadTimeout.stop();
    }
}
